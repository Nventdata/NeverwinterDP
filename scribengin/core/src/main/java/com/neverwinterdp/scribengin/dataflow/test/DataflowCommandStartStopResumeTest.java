package com.neverwinterdp.scribengin.dataflow.test;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.neverwinterdp.registry.ErrorCode;
import com.neverwinterdp.registry.Registry;
import com.neverwinterdp.registry.RegistryException;
import com.neverwinterdp.registry.event.WaitingOrderNodeEventListener;
import com.neverwinterdp.registry.notification.Notifier;
import com.neverwinterdp.scribengin.ScribenginClient;
import com.neverwinterdp.scribengin.client.shell.ScribenginShell;
import com.neverwinterdp.scribengin.dataflow.DataflowClient;
import com.neverwinterdp.scribengin.dataflow.DataflowLifecycleStatus;
import com.neverwinterdp.scribengin.dataflow.DataflowRegistry;
import com.neverwinterdp.scribengin.dataflow.event.DataflowEvent;
import com.neverwinterdp.util.ExceptionUtil;
import com.neverwinterdp.util.text.TabularFormater;

public class DataflowCommandStartStopResumeTest extends DataflowCommandTest {
  final static public String TEST_NAME = "start-stop-resume";

  @Parameter(names = "--dataflow-id", description = "The command should repeat in this failurePeriod of time")
  String dataflowId = "kafka-to-kafka-1";
  
  @Parameter(names = "--wait-before-start", description = "The command should repeat in this failurePeriod of time")
  long waitBeforeStart = -1;
  
  @Parameter(names = "--sleep-before-stop", description = "The command should repeat in this failurePeriod of time")
  long sleepBeforeStop = 10000;
  
  @Parameter(names = "--sleep-before-resume", description = "The command should repeat in this failurePeriod of time")
  long sleepBeforeResume = 10000;
  
  
  @Parameter(names = "--max-wait-for-stop", description = "The command should repeat in this failurePeriod of time")
  long maxWaitForStop = 20000;
  
  @Parameter(names = "--max-wait-for-resume", description = "The command should repeat in this failurePeriod of time")
  long maxWaitForResume = 20000;

  @Parameter(names = "--print-summary", description = "Enable to dump the registry at the end")
  protected boolean printSummary = false;
  
  @Parameter(names = "--max-execution", description = "The maximum number of start/stop/resume execution")
  int maxExecution = 3;
  
  //TODO: implement junit report for this. 
  @Parameter(names = "--junit-report", description = "Enable to dump the registry at the end")
  String  junitReportFile = null;
  
  public void doRun(ScribenginShell shell) throws Exception {
    int stopCount = 0; 
    int stopCompleteCount = 0; 
    int resumeCount = 0 ;
    int resumeCompleteCount = 0; 
    List<ExecuteLog> executeLogs = new ArrayList<ExecuteLog>() ;
    Notifier notifier = null ;
    try {
      ScribenginClient scribenginClient = shell.getScribenginClient() ;
      Registry registry = scribenginClient.getRegistry() ;
      DataflowClient dflClient = scribenginClient.getDataflowClient(dataflowId);
      DataflowLifecycleStatus dataflowStatus = dflClient.getStatus();
      
      if(waitBeforeStart > 0) {
        Thread.sleep(waitBeforeStart);
      }
      int count = 0 ;
      while(count < maxExecution) {
        count++ ;
        notifier = 
            new Notifier(registry, "/scribengin/tests/dataflow-start-top-resume/notification", "simulation-" + (count + 1));
        notifier.initRegistry();
        notifier.info("wait-before-stop", "Wait " + sleepBeforeStop + "ms before stop the dataflow");
        if(sleepBeforeStop > 0) Thread.sleep(sleepBeforeStop);
        try {
          dataflowStatus = dflClient.getStatus();
        } catch(RegistryException ex) {
          notifier.info("unexpected-error", ExceptionUtil.getStackTrace(ex));
          if(ex.getErrorCode() == ErrorCode.NoNode) break;
          throw ex;
        }
        notifier.info("check-dataflow-status", "The current dataflow status is " + dataflowStatus);
        if(dataflowStatus == DataflowLifecycleStatus.FINISH || dataflowStatus == DataflowLifecycleStatus.TERMINATED) {
          notifier.info("terminate", "Terminate the failure simulation, the dataflow status is " + dataflowStatus);
          break;
        }
        stopCount++ ;
        DataflowEvent stopEvent = stopCount % 2 == 0 ? DataflowEvent.PAUSE : DataflowEvent.STOP;
        notifier.info("before-stop", "Before execute stop with the event" + stopEvent);
        ExecuteLog stopExecuteLog = doStop(dflClient, stopEvent) ;
        notifier.info("after-stop", stopExecuteLog.getFormatText());
        executeLogs.add(stopExecuteLog);
        shell.console().println(stopExecuteLog.getFormatText());
        if(!stopExecuteLog.isSuccess()) {
          break;
        }
        stopCompleteCount++ ;

        notifier.info("sleep-before-resume", "Sleep " + sleepBeforeResume +"ms before resume");
        if(sleepBeforeResume > 0) Thread.sleep(sleepBeforeResume);

        resumeCount++ ;
        notifier.info("before-resume", "Before resume");
        ExecuteLog resumeExecuteLog = doResume(dflClient) ;
        notifier.info("after-resume", resumeExecuteLog.getFormatText());
        executeLogs.add(resumeExecuteLog);
        shell.console().println(resumeExecuteLog.getFormatText());
        if(!resumeExecuteLog.isSuccess()) {
          break;
        }
        resumeCompleteCount++ ;
      }
    } catch(Exception ex) {
      ex.printStackTrace();
      if(notifier != null) notifier.info("unexpected-error", ExceptionUtil.getStackTrace(ex));
      shell.execute("registry dump");
      shell.execute("dataflow info --dataflow-id " + dataflowId);
    }
    
    if(printSummary) {
      TabularFormater formater = new TabularFormater("#", "Description", "Duration", "Success") ;
      formater.setTitle("Start/Pause/Stop/Resume Test Summary");
      for(int i = 0; i < executeLogs.size(); i++) {
        ExecuteLog sel = executeLogs.get(i) ;
        Object[] cells = {
          (i + 1), sel.getDescription(), (sel.getStop() - sel.getStart()) + "ms", sel.isSuccess() 
        };
        formater.addRow(cells);
      }
      shell.console().println(formater.getFormatText());
      shell.console().println(
          "stop = " + stopCount +", stop complete = " + stopCompleteCount + ", " +
          "resume = " + resumeCount + ", resume complete = " + resumeCompleteCount);
    }
    
    if(junitReportFile != null) {
      //TODO: look into this junitReport and implement it
      junitReport(junitReportFile, executeLogs) ;
    }
  }
  
  ExecuteLog doStop(DataflowClient dflClient, DataflowEvent stopEvent) throws Exception {
    DataflowRegistry dflRegistry = dflClient.getDataflowRegistry();
    ExecuteLog executeLog = new ExecuteLog("Stop the dataflow with the event " + stopEvent) ;
    executeLog.start(); 
    DataflowLifecycleStatus expectStatus = DataflowLifecycleStatus.PAUSE;
    if(stopEvent == DataflowEvent.STOP) {
      expectStatus = DataflowLifecycleStatus.STOP;
    }
    System.err.println("Client: start request stop, event = " + stopEvent + ", expect status = " + expectStatus);
    WaitingOrderNodeEventListener stopWaitingListener = new WaitingOrderNodeEventListener(dflClient.getRegistry());
    stopWaitingListener.add(dflRegistry.getStatusNode().getPath(), expectStatus, "Wait for dataflow status " + expectStatus, true);
    dflClient.setDataflowEvent(stopEvent);
    stopWaitingListener.waitForEvents(maxWaitForStop);
    if(stopWaitingListener.getUndetectNodeEventCount() > 0) {
      executeLog.setSuccess(false);
    }
    System.err.println("Client: finish request stop, success = " + executeLog.isSuccess() + ", undetect event = " + stopWaitingListener.getUndetectNodeEventCount());
    executeLog.addLog(stopWaitingListener.getTabularFormaterEventLogInfo().getFormatText());
    executeLog.stop();
    return executeLog;
  }
  
  ExecuteLog doResume(DataflowClient dflClient) throws Exception {
    ExecuteLog executeLog = new ExecuteLog("Resume the dataflow") ;
    executeLog.start();
    System.err.println("Client: start request resume...");
    WaitingOrderNodeEventListener resumeWaitingListener = new WaitingOrderNodeEventListener(dflClient.getRegistry());
    String statusPath = dflClient.getDataflowRegistry().getStatusNode().getPath();
    resumeWaitingListener.add(statusPath, DataflowLifecycleStatus.RUNNING, "Expect RUNNING for the dataflow status", true);
    dflClient.setDataflowEvent(DataflowEvent.RESUME);
    resumeWaitingListener.waitForEvents(maxWaitForResume);
    if(resumeWaitingListener.getUndetectNodeEventCount() > 0) {
      executeLog.setSuccess(false);
    }
    System.err.println("Client: finish request stop, success = " + executeLog.isSuccess() + ", undetect event = " + resumeWaitingListener.getUndetectNodeEventCount());
    executeLog.addLog(resumeWaitingListener.getTabularFormaterEventLogInfo().getFormatText());
    executeLog.stop();
    return executeLog ;
  }
}