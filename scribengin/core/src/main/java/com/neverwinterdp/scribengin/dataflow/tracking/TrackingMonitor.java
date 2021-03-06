package com.neverwinterdp.scribengin.dataflow.tracking;

import java.util.List;

import com.beust.jcommander.Parameter;
import com.neverwinterdp.scribengin.dataflow.registry.DataflowRegistry;
import com.neverwinterdp.scribengin.shell.ScribenginShell;
import com.neverwinterdp.vm.client.shell.CommandInput;
import com.neverwinterdp.vm.client.shell.Shell;
import com.neverwinterdp.vm.client.shell.SubCommand;

public class TrackingMonitor extends SubCommand {
  @Parameter(names = "--dataflow-id", required=true, description = "The dataflow id")
  private String dataflowId ;
  
  @Parameter(names = "--show-history-vm", description = "Show the history dataflow worker")
  boolean historyWorkers = false;
  
  @Parameter(names = "--print-period", description = "Pring the report")
  private long printPeriod = 15000;
  
  @Parameter(names = "--max-runtime", description = "The max runtime")
  private long maxRuntime = -1;
  
  @Override
  public void execute(Shell shell, CommandInput cmdInput) throws Exception {
    ScribenginShell scribenginShell = (ScribenginShell) shell;
    String dataflowPath = DataflowRegistry.getDataflowPath(dataflowId);
    TrackingRegistry trackingRegistry = 
      new TrackingRegistry(scribenginShell.getVMClient().getRegistry(), dataflowPath + "/tracking-reports", false);
    
    if(maxRuntime <= 0) {
      report(scribenginShell, trackingRegistry);
    } else {
      long stopTime = System.currentTimeMillis() + maxRuntime;
      boolean finished = false;
      while(!finished) {
        finished = report(scribenginShell, trackingRegistry);
        if(finished) break;
        if(stopTime < System.currentTimeMillis()) break;
        Thread.sleep(printPeriod);
      }
    }
  }
  
  boolean report(ScribenginShell shell, TrackingRegistry trackingRegistry) throws Exception {
    StringBuilder command = new StringBuilder();
    command.append("dataflow info --dataflow-id " + dataflowId + " --show-tasks --show-vm");
    if(historyWorkers) command.append(" --show-history-vm ");
    shell.execute(command.toString());

    List<TrackingMessageReport> generatedReports = trackingRegistry.getGeneratorReports();
    List<TrackingMessageReport> validatedReports = trackingRegistry.getValidatorReports();
    shell.console().print(TrackingMessageReport.getFormattedReport("Generated Report", generatedReports));
    shell.console().print(TrackingMessageReport.getFormattedReport("Validated Report", validatedReports));

    if(validatedReports.size() == 0) return false;
    boolean validateAllMessges = true;
    for(TrackingMessageReport selReport : validatedReports) {
      if(selReport.getNumOfMessage() == selReport.getNoLostTo()) {
        continue ;
      } else {
        validateAllMessges = false;
        break;
      }
    }
    return validateAllMessges;
  }

  @Override
  public String getDescription() { return "Tracking Monitor command"; }
}
