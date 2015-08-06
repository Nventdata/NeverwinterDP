package com.neverwinterdp.scribengin.dataflow;

import static com.neverwinterdp.vm.tool.VMClusterBuilder.h1;

import java.util.List;

import com.neverwinterdp.registry.Registry;
import com.neverwinterdp.registry.RegistryException;
import com.neverwinterdp.registry.SequenceIdTracker;
import com.neverwinterdp.scribengin.ScribenginClient;
import com.neverwinterdp.scribengin.dataflow.registry.DataflowRegistry;
import com.neverwinterdp.scribengin.dataflow.util.DataflowFormater;
import com.neverwinterdp.scribengin.dataflow.util.DataflowRegistryDebugger;
import com.neverwinterdp.scribengin.service.ScribenginService;
import com.neverwinterdp.scribengin.service.VMScribenginServiceCommand;
import com.neverwinterdp.util.JSONSerializer;
import com.neverwinterdp.vm.VMDescriptor;
import com.neverwinterdp.vm.client.VMClient;
import com.neverwinterdp.vm.command.Command;
import com.neverwinterdp.vm.command.CommandResult;
import com.neverwinterdp.yara.snapshot.ClusterMetricRegistrySnapshot;
import com.neverwinterdp.yara.snapshot.MetricRegistrySnapshot;

public class DataflowSubmitter {
  private ScribenginClient   scribenginClient;
  private String             dfsDataflowHome;
  private DataflowDescriptor dflDescriptor;

  public DataflowSubmitter(ScribenginClient scribenginClient, String dfsDataflowHome, DataflowDescriptor dflDescriptor) {
    this.scribenginClient  = scribenginClient;
    this.dfsDataflowHome = dfsDataflowHome;
    this.dflDescriptor     = dflDescriptor;
  }

  public DataflowDescriptor getDataflowDescriptor() { return this.dflDescriptor ; }
  
  public void submit() throws Exception {
    Registry registry = scribenginClient.getRegistry();
    VMClient vmClient = scribenginClient.getVMClient() ;
    if(dflDescriptor.getId() == null) {
      SequenceIdTracker dataflowIdTracker = new SequenceIdTracker(registry, ScribenginService.DATAFLOW_ID_TRACKER) ;
      dflDescriptor.setId( dataflowIdTracker.nextSeqId() + "-" + dflDescriptor.getName());
    }

    dflDescriptor.setDataflowAppHome(dfsDataflowHome);
    
    h1("Submit The Dataflow " + dflDescriptor.getId());
    System.out.println(JSONSerializer.INSTANCE.toString(dflDescriptor)) ;
    VMDescriptor scribenginMaster = scribenginClient.getScribenginMaster();
    Command deployCmd = new VMScribenginServiceCommand.DataflowDeployCommand(dflDescriptor) ;
    CommandResult<Boolean> result = 
        (CommandResult<Boolean>)vmClient.execute(scribenginMaster, deployCmd);
  }
  
  void waitForEqualOrGreaterThanStatus(long timeout, DataflowLifecycleStatus status) throws Exception {
    DataflowClient dflClient = scribenginClient.getDataflowClient(dflDescriptor.getId(), 90000);
    dflClient.waitForEqualOrGreaterThanStatus(3000, timeout, status);
  }
  
  public void waitForRunning(long timeout) throws Exception {
    waitForEqualOrGreaterThanStatus(timeout, DataflowLifecycleStatus.RUNNING) ;
  }
  
  public void waitForFinish(long timeout) throws Exception {
    waitForEqualOrGreaterThanStatus(timeout, DataflowLifecycleStatus.FINISH) ;
  }
  
  public DataflowSubmitter enableDataflowTaskDebugger(Appendable out) throws Exception {
    DataflowRegistryDebugger debugger = scribenginClient.getDataflowRegistryDebugger(out, dflDescriptor);
    debugger.enableDataflowDebugger();
    return this ;
  }
  
  public DataflowSubmitter enableAllDebugger(Appendable out) throws Exception {
    DataflowRegistryDebugger debugger = scribenginClient.getDataflowRegistryDebugger(out, dflDescriptor);
    debugger.enableDataflowTaskDebugger(false);
    debugger.enableDataflowVMDebugger(false);
    debugger.enableDataflowActivityDebugger(false);
    return this ;
  }
  
  public  ClusterMetricRegistrySnapshot getDataflowMetrics() throws RegistryException {
    ClusterMetricRegistrySnapshot dflMetrics = new ClusterMetricRegistrySnapshot(dflDescriptor.getId()) ;
    String dataflowPath = ScribenginService.getDataflowPath(dflDescriptor.getId());
    List<MetricRegistrySnapshot> snapshots = DataflowRegistry.getMetrics(scribenginClient.getRegistry(), dataflowPath) ;
    for(MetricRegistrySnapshot sel : snapshots) {
      dflMetrics.add(sel);
    }
    return dflMetrics ;
  }
  
  public void report(Appendable out) throws Exception {
    String dataflowPath = ScribenginService.getDataflowPath(dflDescriptor.getId());
    DataflowFormater dflFt = new DataflowFormater(scribenginClient.getRegistry(), dataflowPath);
    out.append("Dataflow " + dflDescriptor.getId()).append('\n');
    out.append("**************************************************************************************").append('\n');
    out.append(dflFt.getFormattedText()).append('\n');
    
    List<MetricRegistrySnapshot> snapshots = DataflowRegistry.getMetrics(scribenginClient.getRegistry(), dataflowPath) ;
    out.append(MetricRegistrySnapshot.getFormattedText(snapshots));
    
    out.append("**************************************************************************************").append("\n\n");
  }
  
  public void dumpDataflowRegistry(Appendable out) throws Exception {
    String dataflowStatusPath = ScribenginService.getDataflowPath(dflDescriptor.getId());
    scribenginClient.getRegistry().get(dataflowStatusPath).dump(out);
    report(out);
  }
}
