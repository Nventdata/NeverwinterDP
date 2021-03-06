package com.neverwinterdp.scribengin.dataflow.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.neverwinterdp.registry.ErrorCode;
import com.neverwinterdp.registry.Node;
import com.neverwinterdp.registry.NodeCreateMode;
import com.neverwinterdp.registry.RefNode;
import com.neverwinterdp.registry.Registry;
import com.neverwinterdp.registry.RegistryException;
import com.neverwinterdp.registry.Transaction;
import com.neverwinterdp.registry.task.TaskExecutorDescriptor;
import com.neverwinterdp.registry.txevent.TXEventBroadcaster;
import com.neverwinterdp.scribengin.dataflow.runtime.worker.DataflowWorkerRuntimeReport;
import com.neverwinterdp.scribengin.dataflow.runtime.worker.DataflowWorkerStatus;
import com.neverwinterdp.vm.VMDescriptor;
import com.neverwinterdp.vm.service.VMService;
import com.neverwinterdp.yara.MetricRegistry;
import com.neverwinterdp.yara.snapshot.MetricRegistrySnapshot;

public class WorkerRegistry {
  final static public String ALL_WORKERS_PATH       = "workers/all";
  final static public String ACTIVE_WORKERS_PATH    = "workers/active";
  final static public String HISTORY_WORKERS_PATH   = "workers/history";
  final static public String WORKER_EVENT_PATH      = "workers/events" ;
  
  private Registry           registry;
  private String             dataflowPath;

  private Node               workers;
  private Node               allWorkers;
  private Node               activeWorkers;
  private Node               historyWorkers;
  private TXEventBroadcaster workerEventBroadcaster;

  public WorkerRegistry(Registry registry, String dataflowPath) throws RegistryException {
    this.registry         = registry;
    this.dataflowPath     = dataflowPath;
    workers = registry.get(dataflowPath + "/workers");
    allWorkers = registry.get(dataflowPath + "/" + ALL_WORKERS_PATH);
    activeWorkers = registry.get(dataflowPath + "/" + ACTIVE_WORKERS_PATH);
    historyWorkers = registry.get(dataflowPath + "/" + HISTORY_WORKERS_PATH);
    workerEventBroadcaster = new TXEventBroadcaster(registry, dataflowPath + "/" + WORKER_EVENT_PATH, false);
    
  }
  
  void create(Transaction transaction) throws RegistryException {
  }
  
  void initRegistry(Transaction transaction) throws RegistryException {
    transaction.create(workers, null, NodeCreateMode.PERSISTENT);
    transaction.create(allWorkers, null, NodeCreateMode.PERSISTENT);
    transaction.create(activeWorkers, null, NodeCreateMode.PERSISTENT);
    transaction.create(historyWorkers, null, NodeCreateMode.PERSISTENT);
    workerEventBroadcaster.initRegistry(transaction);
  }
  
  public Node getWorkerNode(String vmId) throws RegistryException { 
    return allWorkers.getChild(vmId) ; 
  }
  
  public DataflowWorkerStatus[] getActiveWorkerStatus() throws RegistryException {
    List<String> vmIds = activeWorkers.getChildren();
    DataflowWorkerStatus[] status = new DataflowWorkerStatus[vmIds.size()];
    for(int i = 0; i < vmIds.size(); i++) {
      status[i] = getDataflowWorkerStatus(vmIds.get(i)) ;
      
    }
    return status ;
  }
  
  public TXEventBroadcaster getWorkerEventBroadcaster() { return workerEventBroadcaster; }
  
  public List<VMDescriptor> getActiveWorkers() throws RegistryException {
    List<String> activeWorkerIds = activeWorkers.getChildren();
    return allWorkers.getSelectRefChildrenAs(activeWorkerIds, VMDescriptor.class) ;
  }
  
  public VMDescriptor findActiveWorker(String workerId) throws RegistryException {
    if(activeWorkers.hasChild(workerId)) {
      return allWorkers.getRefChildAs(workerId, VMDescriptor.class);
    }
    return null;
  }
  
  public List<String> getActiveWorkerIds() throws RegistryException {
    return activeWorkers.getChildren();
  }

  public int countActiveDataflowWorkers() throws RegistryException {
    return activeWorkers.getChildren().size();
  }
  
  public List<VMDescriptor> getAllWorkers() throws RegistryException {
    List<String> activeWorkerIds = allWorkers.getChildren();
    return allWorkers.getSelectRefChildrenAs(activeWorkerIds, VMDescriptor.class) ;
  }

  public List<String> getAllWorkerIds() throws RegistryException {
    return allWorkers.getChildren();
  }
  
  public void addWorker(VMDescriptor vmDescriptor) throws RegistryException {
    Transaction transaction = registry.getTransaction() ;
    RefNode refNode = new RefNode(vmDescriptor.getRegistryPath()) ;
    transaction.createChild(allWorkers, vmDescriptor.getVmId(), refNode, NodeCreateMode.PERSISTENT) ;
    transaction.createDescendant(allWorkers, vmDescriptor.getVmId() + "/status", DataflowWorkerStatus.CREATE, NodeCreateMode.PERSISTENT) ;
    transaction.createChild(activeWorkers, vmDescriptor.getVmId(), NodeCreateMode.PERSISTENT) ;
    transaction.commit();
  }
  
  public void setWorkerStatus(VMDescriptor vmDescriptor, DataflowWorkerStatus status) throws RegistryException {
    setWorkerStatus(vmDescriptor.getVmId(), status);
  }
  
  public void setWorkerStatus(String vmId, DataflowWorkerStatus status) throws RegistryException {
    Node workerNode = allWorkers.getChild(vmId);
    Node statusNode = workerNode.getChild("status");
    statusNode.setData(status);
  }
  
  public void cleanDisconnectedWorkers() throws RegistryException {
    Set<String> activeVMIds = new HashSet<String>(VMService.getActiveVMIds(registry));
    List<String> activeWorkerVMIds= this.getActiveWorkerIds();
    for(String activeWorkerVMId : activeWorkerVMIds) {
      if(!activeVMIds.contains(activeWorkerVMId)) {
        historyWorker(activeWorkerVMId);
      }
    }
  }
  
  public void historyWorker(String vmId) throws RegistryException {
    Transaction transaction = registry.getTransaction() ;
    historyWorker(transaction, vmId);
    transaction.commit();
  }
  
  void historyWorker(Transaction transaction, String vmId) throws RegistryException {
    transaction.createChild(historyWorkers, vmId, NodeCreateMode.PERSISTENT) ;
    transaction.deleteChild(activeWorkers, vmId) ;
  }
  
  public void createWorkerTaskExecutor(VMDescriptor vmDescriptor, TaskExecutorDescriptor descriptor) throws RegistryException {
    Node worker = allWorkers.getChild(vmDescriptor.getVmId()) ;
    Node executors = worker.createDescendantIfNotExists("executors");
    executors.createChild(descriptor.getId(), descriptor, NodeCreateMode.PERSISTENT);
  }
  
  public void updateWorkerTaskExecutor(VMDescriptor vmDescriptor, TaskExecutorDescriptor descriptor) throws RegistryException {
    Node worker = allWorkers.getChild(vmDescriptor.getVmId()) ;
    Node executor = worker.getDescendant("executors/" + descriptor.getId()) ;
    executor.setData(descriptor);
  }
  
  public DataflowWorkerStatus getDataflowWorkerStatus(String vmId) throws RegistryException {
    return allWorkers.getChild(vmId).getChild("status").getDataAs(DataflowWorkerStatus.class);
  }
  
  public List<TaskExecutorDescriptor> getWorkerExecutors(String worker) throws RegistryException {
    Node executors = allWorkers.getDescendant(worker + "/executors") ;
    return executors.getChildrenAs(TaskExecutorDescriptor.class);
  }
  
  public void waitForWorkerStatus(DataflowWorkerStatus status, long checkPeriod, long timeout) throws Exception {
    long stopTime = System.currentTimeMillis() + timeout;
    while(stopTime > System.currentTimeMillis()) {
      boolean ok = true;
      for(DataflowWorkerStatus selStatus : getActiveWorkerStatus()) {
        if(selStatus == null) continue;
        if(!selStatus.equalOrGreaterThan(status)) {
          ok = false;
          break;
        }
      }
      if(ok) return;
      Thread.sleep(checkPeriod);
    }
    throw new Exception("Not all dataflow worker have the " + status + ", after " + timeout + "ms");
  }
  
  public void createMetric(String vmName, MetricRegistry mRegistry) throws RegistryException {
    MetricRegistrySnapshot mRegistrySnapshot = new MetricRegistrySnapshot(vmName, mRegistry) ;
    allWorkers.getChild(vmName).createChild("metrics", mRegistrySnapshot, NodeCreateMode.PERSISTENT);
  }
  
  public void saveMetric(String vmName, MetricRegistry mRegistry) throws RegistryException {
    Node metricsNode = allWorkers.getDescendant(vmName + "/metrics");
    MetricRegistrySnapshot mRegistrySnapshot = new MetricRegistrySnapshot(vmName, mRegistry) ;
    metricsNode.setData( mRegistrySnapshot);
  }
  
  public MetricRegistrySnapshot getMetric(String vmName) throws RegistryException {
    Node metricsNode = allWorkers.getDescendant(vmName + "/metrics");
    MetricRegistrySnapshot mRegistrySnapshot = metricsNode.getDataAs(MetricRegistrySnapshot.class) ;
    return mRegistrySnapshot;
  }
  
  public List<MetricRegistrySnapshot> getMetrics() throws RegistryException {
    List<String> vmNames = allWorkers.getChildren() ;
    List<String> paths = new ArrayList<>();
    for(String vmName : vmNames) {
      paths.add(allWorkers.getPath() + "/" + vmName + "/metrics");
    }
    return registry.getDataAs(paths, MetricRegistrySnapshot.class);
  }
  
  public List<DataflowWorkerRuntimeReport> getAllDataflowWorkerRuntimeReports() throws RegistryException {
    return getDataflowWorkerRuntimeReports(registry, dataflowPath, "all");
  }
  
  public List<DataflowWorkerRuntimeReport> getActiveDataflowWorkerRuntimeReports() throws RegistryException {
    return getDataflowWorkerRuntimeReports(registry, dataflowPath, "active");
  }
  
  public List<DataflowWorkerRuntimeReport> getHistoryDataflowWorkerRuntimeReports() throws RegistryException {
    return getDataflowWorkerRuntimeReports(registry, dataflowPath, "history");
  }
  
  static public List<DataflowWorkerRuntimeReport> getAllDataflowWorkerRuntimeReports(Registry registry, String dataflowPath) throws RegistryException {
    return getDataflowWorkerRuntimeReports(registry, dataflowPath, "all");
  }
  
  static public List<DataflowWorkerRuntimeReport> getActiveDataflowWorkerRuntimeReports(Registry registry, String dataflowPath) throws RegistryException {
    return getDataflowWorkerRuntimeReports(registry, dataflowPath, "active");
  }
  
  static public List<DataflowWorkerRuntimeReport> getHistoryDataflowWorkerRuntimeReports(Registry registry, String dataflowPath) throws RegistryException {
    return getDataflowWorkerRuntimeReports(registry, dataflowPath, "history");
  }
  
  static public List<DataflowWorkerRuntimeReport> getDataflowWorkerRuntimeReports(Registry registry, String dataflowPath, String category) throws RegistryException {
    try {
      String workerAllPath  = dataflowPath + "/workers/all";
      String workerListPath = dataflowPath + "/workers/" + category;
      List<String> workerIds = registry.getChildren(workerListPath) ;
      List<DataflowWorkerRuntimeReport> holder = new ArrayList<>();
      for(String selWorkerId : workerIds) {
        holder.add(new DataflowWorkerRuntimeReport(registry, workerAllPath + "/" + selWorkerId));
      }
      return holder;
    } catch(RegistryException ex) {
      if(ex.getErrorCode() == ErrorCode.NoNode) return new ArrayList<>();
      throw ex;
    }
  }
}