package com.neverwinterdp.scribengin.dataflow.worker;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.neverwinterdp.module.AppContainer;
import com.neverwinterdp.module.DataflowWorkerModule;
import com.neverwinterdp.module.ESOSMonitorLoggerModule;
import com.neverwinterdp.module.ServiceModuleContainer;
import com.neverwinterdp.scribengin.dataflow.DataflowRegistry;
import com.neverwinterdp.vm.VMApp;
import com.neverwinterdp.vm.VMConfig;
import com.neverwinterdp.vm.VMConfig.ClusterEnvironment;
import com.neverwinterdp.yara.MetricPrinter;
import com.neverwinterdp.yara.MetricRegistry;


public class VMDataflowWorkerApp extends VMApp {
  private Logger logger  ;
  
  private DataflowTaskExecutorService dataflowTaskExecutorService;
  
  @Override
  public void run() throws Exception {
    final VMConfig vmConfig = getVM().getDescriptor().getVmConfig();
    logger = getVM().getLoggerFactory().getLogger(VMDataflowWorkerApp.class);
    
    AppContainer appContainer = getVM().getAppContainer();
    Map<String, String> esLoggerModuleProps = new HashMap<String, String>();
    //appContainer.install(esLoggerModuleProps, ESOSMonitorLoggerModule.NAME);
    
    Map<String, String> dataflowWorkerModuleProps = new HashMap<String, String>();
    dataflowWorkerModuleProps.putAll(vmConfig.getHadoopProperties());
    if(vmConfig.getClusterEnvironment() ==  ClusterEnvironment.JVM) {
      dataflowWorkerModuleProps.put("cluster.environment", "jvm");
    } else {
      dataflowWorkerModuleProps.put("cluster.environment", "yarn");
    }
    
    appContainer.install(dataflowWorkerModuleProps, DataflowWorkerModule.NAME);
    ServiceModuleContainer dataflowWorkerModuleContainer = appContainer.getModule(DataflowWorkerModule.NAME);
    
    dataflowWorkerModuleContainer.getInstance(DataflowRegistry.class).addWorker(getVM().getDescriptor());
    dataflowTaskExecutorService = dataflowWorkerModuleContainer.getInstance(DataflowTaskExecutorService.class);
    addListener(new VMApp.VMAppTerminateEventListener() {
      @Override
      public void onEvent(VMApp vmApp, TerminateEvent terminateEvent) {
        try {
          if(terminateEvent == TerminateEvent.Shutdown) {
            dataflowTaskExecutorService.shutdown();
          } else if(terminateEvent == TerminateEvent.SimulateKill) {
            dataflowTaskExecutorService.simulateKill();
          } else if(terminateEvent == TerminateEvent.Kill) {
            logger.info("Execute the kill event with System.exit(0)");
            System.exit(0);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
    
    try {
      dataflowTaskExecutorService.start();
      dataflowTaskExecutorService.waitForTerminated(500);
    } catch(InterruptedException ex) {
    } finally {
      StringBuilder out = new StringBuilder() ;
      MetricPrinter metricPrinter = new MetricPrinter(out) ;
      MetricRegistry mRegistry = dataflowWorkerModuleContainer.getInstance(MetricRegistry.class);
      DataflowRegistry dflRegistry = dataflowTaskExecutorService.getDataflowRegistry() ;
      dflRegistry.saveMetric(getVM().getDescriptor().getVmConfig().getName(), mRegistry);
      metricPrinter.print(mRegistry);
      System.out.println(out.toString());
    }
  }
}