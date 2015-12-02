package com.neverwinterdp.scribengin.dataflow.worker;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.neverwinterdp.registry.Node;
import com.neverwinterdp.registry.RegistryException;
import com.neverwinterdp.registry.notification.Notifier;
import com.neverwinterdp.registry.task.TaskExecutorDescriptor;
import com.neverwinterdp.registry.task.dedicated.DedicatedTaskContext;
import com.neverwinterdp.registry.task.dedicated.DedicatedTaskService;
import com.neverwinterdp.registry.task.dedicated.TaskSlotExecutor;
import com.neverwinterdp.registry.task.dedicated.TaskSlotExecutorFactory;
import com.neverwinterdp.registry.txevent.TXEvent;
import com.neverwinterdp.registry.txevent.TXEventBroadcaster;
import com.neverwinterdp.registry.txevent.TXEventNotification;
import com.neverwinterdp.registry.txevent.TXEventWatcher;
import com.neverwinterdp.scribengin.dataflow.DataflowEvent;
import com.neverwinterdp.scribengin.dataflow.config.DataflowConfig;
import com.neverwinterdp.scribengin.dataflow.operator.OperatorTaskConfig;
import com.neverwinterdp.scribengin.dataflow.operator.OperatorTaskSlotExecutor;
import com.neverwinterdp.scribengin.dataflow.registry.DataflowRegistry;
import com.neverwinterdp.storage.StorageService;
import com.neverwinterdp.util.log.LoggerFactory;
import com.neverwinterdp.vm.VMDescriptor;
import com.neverwinterdp.yara.MetricRegistry;

public class WorkerService {
private Logger logger ;
  
  @Inject
  private VMDescriptor vmDescriptor;
 
  @Inject
  private DataflowRegistry dflRegistry;
  
  @Inject
  private StorageService storageService ;
  
  @Inject
  private MetricRegistry   metricRegistry ;
  
  private DedicatedTaskService<OperatorTaskConfig> taskService;
  
  private DataflowWorkerEventWatcher   dataflowWorkerEventWatcher ;

  private DataflowWorkerStatus workerStatus = DataflowWorkerStatus.INIT;
  
  private Notifier         notifier ;
  private boolean simulateKill = false ;
  
  public Logger getLogger() { return logger; }
  
  public VMDescriptor getVMDescriptor() { return vmDescriptor; }
  
  public DataflowRegistry getDataflowRegistry() { return dflRegistry; }
  
  public StorageService getStorageService() { return storageService; }

  public MetricRegistry getMetricRegistry() { return metricRegistry; }
  
  @Inject
  public void onInject(Injector container, LoggerFactory lfactory) throws Exception {
    logger = lfactory.getLogger(WorkerService.class);
  }
  
  public void init() throws Exception {
    System.out.println("DataflowWorkerService: init()");
    workerStatus = DataflowWorkerStatus.INIT;
    dflRegistry.getWorkerRegistry().setWorkerStatus(vmDescriptor, workerStatus);
    
    TaskSlotExecutorFactory<OperatorTaskConfig> taskSlotExecutorFactory = new TaskSlotExecutorFactory<OperatorTaskConfig>() {
      @Override
      public TaskSlotExecutor<OperatorTaskConfig> create(DedicatedTaskContext<OperatorTaskConfig> context) throws Exception {
        return new  OperatorTaskSlotExecutor(WorkerService.this, context);
      }
    };
    
    DataflowConfig dflConfig = dflRegistry.getConfigRegistry().getDataflowConfig();
    taskService = new DedicatedTaskService<OperatorTaskConfig>(dflRegistry.getTaskRegistry(), taskSlotExecutorFactory);
    for(int i = 0; i < dflConfig.getWorker().getNumOfExecutor(); i++) {
      TaskExecutorDescriptor executor = new TaskExecutorDescriptor(vmDescriptor.getId() + "-executor-" + i, vmDescriptor.getId());
      taskService.addExecutor(executor, 2);
    }
    Node workerNode = dflRegistry.getWorkerRegistry().getWorkerNode(vmDescriptor.getId());
    notifier = new Notifier(dflRegistry.getRegistry(), workerNode.getPath() + "/notifications", "dataflow-worker-service");
    notifier.initRegistry();
    
    TXEventBroadcaster broadcaster = dflRegistry.getWorkerRegistry().getWorkerEventBroadcaster();
    String workerEvtPath = broadcaster.getEventPath();
    dataflowWorkerEventWatcher = new DataflowWorkerEventWatcher(dflRegistry, workerEvtPath, vmDescriptor.getId());
  }
  
  public void run() throws Exception {
    System.out.println("DataflowMasterService: run()");
    workerStatus = DataflowWorkerStatus.RUNNING;
    dflRegistry.getWorkerRegistry().setWorkerStatus(vmDescriptor, workerStatus);
    taskService.getTaskExecutorService().startExecutors(3000);
  }
  
  public void waitForTermination() throws RegistryException, InterruptedException {
    System.out.println("DataflowWorkerService: waitForTermination()");
    long maxRunTime = dflRegistry.getConfigRegistry().getDataflowConfig().getMaxRunTime();
    try {
      taskService.getTaskExecutorService().awaitTermination(maxRunTime, TimeUnit.MILLISECONDS);
    } catch(InterruptedException ex) {
      if(simulateKill) {
        dataflowWorkerEventWatcher.setComplete();
        throw new RuntimeException("Simulate Kill", ex) ;
      }
      throw ex;
    }
    taskService.onDestroy();
    dataflowWorkerEventWatcher.setComplete();
    workerStatus = DataflowWorkerStatus.TERMINATED;
    dflRegistry.getWorkerRegistry().setWorkerStatus(vmDescriptor, workerStatus);
    dflRegistry.getWorkerRegistry().saveMetric(vmDescriptor.getId(), metricRegistry);
  }
  
  public void shutdown() throws InterruptedException, RegistryException {
    taskService.onDestroy();
    dataflowWorkerEventWatcher.setComplete();
    workerStatus = DataflowWorkerStatus.TERMINATED_WITH_INTERRUPT;
    dflRegistry.getWorkerRegistry().setWorkerStatus(vmDescriptor, workerStatus);
    dflRegistry.getWorkerRegistry().saveMetric(vmDescriptor.getId(), metricRegistry);
  }
  
  public void simulateKill() throws Exception {
    System.err.println("WorkerService: simulateKill()"); 
    logger.info("Start kill()");
    notifier.info("start-simulate-kill", "DataflowTaskExecutorService: start simulateKill()");
    simulateKill = true ;
    if(workerStatus.lessThan(DataflowWorkerStatus.TERMINATED)) {
      System.err.println("WorkerService: taskService.getTaskExecutorService().simulateKill()"); 
      taskService.simulateKill();
    }
    notifier.info("finish-simulate-kill", "DataflowTaskExecutorService: finish simulateKill()");
    logger.info("Finish kill()");
  }
  
  public class DataflowWorkerEventWatcher extends TXEventWatcher {
    public DataflowWorkerEventWatcher(DataflowRegistry dflRegistry, String eventsPath, String clientId) throws RegistryException {
      super(dflRegistry.getRegistry(), eventsPath, clientId);
    }
    
    public void onTXEvent(TXEvent txEvent) throws Exception {
      DataflowEvent taskEvent = txEvent.getDataAs(DataflowEvent.class);
      if(taskEvent == DataflowEvent.PAUSE) {
        logger.info("Dataflow worker detect pause event!");
      } else if(taskEvent == DataflowEvent.STOP) {
        logger.info("Dataflow worker detect stop event!");
        shutdown() ;
      } else if(taskEvent == DataflowEvent.RESUME) {
        logger.info("Dataflow worker detect resume event!");
      }
      notify(txEvent, TXEventNotification.Status.Complete);
    }
  }

}
