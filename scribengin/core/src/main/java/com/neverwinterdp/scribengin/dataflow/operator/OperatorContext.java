package com.neverwinterdp.scribengin.dataflow.operator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.neverwinterdp.scribengin.dataflow.registry.DataflowRegistry;
import com.neverwinterdp.scribengin.dataflow.worker.WorkerService;
import com.neverwinterdp.storage.Record;
import com.neverwinterdp.storage.Storage;
import com.neverwinterdp.storage.StorageConfig;
import com.neverwinterdp.storage.StorageService;
import com.neverwinterdp.storage.sink.Sink;
import com.neverwinterdp.storage.sink.SinkPartitionStream;
import com.neverwinterdp.storage.sink.SinkPartitionStreamWriter;
import com.neverwinterdp.storage.source.SourcePartition;
import com.neverwinterdp.storage.source.SourcePartitionStream;
import com.neverwinterdp.storage.source.SourcePartitionStreamReader;
import com.neverwinterdp.yara.Meter;

public class OperatorContext {
  private WorkerService      workerService;
  private OperatorTaskConfig taskConfig;
  private OperatorTaskReport taskReport;

  private InputContext               inputContext;
  private Map<String, OutputContext> outputContexts = new HashMap<>();
  
  private boolean complete = false;
  private Meter   dataflowReadMeter;
  private Meter   dataflowRecordMeter;

  public OperatorContext(WorkerService workerService, OperatorTaskConfig taskConfig, OperatorTaskReport report) throws Exception {
    this.workerService = workerService;
    this.taskConfig  = taskConfig;
    this.taskReport = report;
    
    dataflowReadMeter   = 
        workerService.getMetricRegistry().getMeter("dataflow.source.throughput.byte", "byte") ;
    dataflowRecordMeter = 
        workerService.getMetricRegistry().getMeter("dataflow.source.throughput.record", "record") ;
    initContext();
  }
  
  void initContext() throws Exception {
    DataflowRegistry dflRegistry = workerService.getDataflowRegistry();
    StorageService storageService = workerService.getStorageService();
    StorageConfig inputConfig = dflRegistry.getStreamRegistry().getStream(taskConfig.getInput()) ;
    Storage inputStorage = storageService.getStorage(inputConfig);
    int partitionId = taskConfig.getInputPartitionId();
    inputContext = new InputContext(inputStorage, partitionId);
    for(String output : taskConfig.getOutputs()) {
      StorageConfig outputConfig = dflRegistry.getStreamRegistry().getStream(output) ;
      Storage outputStorage = storageService.getStorage(outputConfig);
      OutputContext outputContext = new OutputContext(outputStorage, partitionId);
      outputContexts.put(output, outputContext);
    }
  }
  
  public OperatorTaskConfig getTaskConfig() { return this.taskConfig; }
  
  public OperatorTaskReport getTaskReport() { return this.taskReport; }
  
  public WorkerService getWorkerService() { return this.workerService; }
  
  public boolean isComplete() { return this.complete ; }
  
  public void setComplete() { this.complete = true; }
  
  public Set<String> getAvailableOutputs() { return taskConfig.getOutputs(); }
  
  public Record nextRecord(long maxWaitForDataRead) throws Exception {
    Record dataflowMessage = inputContext.assignedPartitionReader.next(maxWaitForDataRead);
    if(dataflowMessage != null) {
      dataflowReadMeter.mark(dataflowMessage.getData().length + dataflowMessage.getKey().length());
      dataflowRecordMeter.mark(1);
    }
    return dataflowMessage ;
  }
  
  public void write(String name, Record record) throws Exception {
    OutputContext sinkContext = outputContexts.get(name);
    sinkContext.assignedPartitionWriter.append(record);
    Meter meter = 
        workerService.getMetricRegistry().getMeter("dataflow.sink." + name + ".throughput.byte", "byte") ;
    meter.mark(record.getData().length + record.getKey().length());
    Meter recordMetter = 
        workerService.getMetricRegistry().getMeter("dataflow.sink." + name + ".throughput.record", "record") ;
    recordMetter.mark(1);
  }
  
  
  private void prepareCommit() throws Exception {
    Iterator<OutputContext> i = outputContexts.values().iterator();
    while (i.hasNext()) {
      OutputContext ctx = i.next();
      ctx.prepareCommit();
    }
    inputContext.prepareCommit();;
  }
  
  private void completeCommit() throws Exception {
    Iterator<OutputContext> i = outputContexts.values().iterator();
    while (i.hasNext()) {
      OutputContext ctx = i.next();
      ctx.completeCommit();
    }
    //The source should commit after sink commit. In the case the source or sink does not support
    //2 phases commit, it will cause the data to duplicate only, not loss
    inputContext.assignedPartitionReader.completeCommit();
  }
  
  public void commit() throws Exception {
    //prepareCommit is a vote to make sure both sink, invalidSink, and source
    //are ready to commit data, otherwise rollback will occur
    try {
      prepareCommit();
      completeCommit();
      taskReport.updateCommit();
      workerService.getDataflowRegistry().getTaskRegistry().save(taskConfig, taskReport);
    } catch (Exception ex) {
      taskReport.setCommitFailCount(taskReport.getCommitFailCount() + 1);
      workerService.getLogger().warn("DataflowTask Commit Fail");
      throw ex;
    } 
  }
  
  public void rollback() throws Exception {
    //TODO: implement the proper transaction
    Iterator<OutputContext> i = outputContexts.values().iterator();
    while (i.hasNext()) {
      OutputContext ctx = i.next();
      ctx.rollback();
    }
    inputContext.rollback();
  }

  public void close() throws Exception {
    //TODO: implement the proper transaction
    Iterator<OutputContext> i = outputContexts.values().iterator();
    while (i.hasNext()) {
      OutputContext ctx = i.next();
      ctx.close();
      ;
    }
    inputContext.close();
  }
  
  static public class InputContext {
    private SourcePartition             source;
    private SourcePartitionStream       assignedPartition;
    private SourcePartitionStreamReader assignedPartitionReader;

    public InputContext(Storage storage, int partitionId) throws Exception {
      this.source = storage.getSource().getLatestSourcePartition();
      this.assignedPartition = source.getPartitionStream(partitionId);
      this.assignedPartitionReader = assignedPartition.getReader("DataflowTask");
    }

    public void prepareCommit() throws Exception {
      assignedPartitionReader.prepareCommit();
    }

    public void completeCommit() throws Exception {
      assignedPartitionReader.completeCommit();
    }

    public void rollback() throws Exception {
      assignedPartitionReader.rollback();
    }

    public void close() throws Exception {
      assignedPartitionReader.close();
    }
  }

  static public class OutputContext {
    private Sink                      sink;
    private SinkPartitionStream       assignedPartition;
    private SinkPartitionStreamWriter assignedPartitionWriter;

    public OutputContext(Storage storage, int partitionId) throws Exception {
      sink = storage.getSink();
      assignedPartition = sink.getParitionStream(partitionId);
      if(assignedPartition == null) {
        assignedPartition = sink.getParitionStream(partitionId);
      }
      assignedPartitionWriter = assignedPartition.getWriter();
    }

    public void prepareCommit() throws Exception {
      assignedPartitionWriter.prepareCommit();
    }

    public void completeCommit() throws Exception {
      assignedPartitionWriter.completeCommit();
    }

    public void rollback() throws Exception {
      assignedPartitionWriter.rollback();
    }

    public void close() throws Exception {
      assignedPartitionWriter.close();
    }
    
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append("Sink:\n").
        append("  Type = ").append(sink.getStorageConfig().getType()).
        append("  Stream Id = ").append(assignedPartition.getPartitionStreamId());
      return b.toString();
    }
  }
}
