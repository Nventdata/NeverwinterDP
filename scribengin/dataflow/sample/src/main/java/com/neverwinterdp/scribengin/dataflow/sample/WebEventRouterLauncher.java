package com.neverwinterdp.scribengin.dataflow.sample;

import com.beust.jcommander.JCommander;
import com.neverwinterdp.message.Message;
import com.neverwinterdp.message.TrackingWindowReport;
import com.neverwinterdp.scribengin.ScribenginClient;
import com.neverwinterdp.scribengin.dataflow.DataSet;
import com.neverwinterdp.scribengin.dataflow.Dataflow;
import com.neverwinterdp.scribengin.dataflow.DataflowClient;
import com.neverwinterdp.scribengin.dataflow.DataflowDescriptor;
import com.neverwinterdp.scribengin.dataflow.DataflowSubmitter;
import com.neverwinterdp.scribengin.dataflow.KafkaDataSet;
import com.neverwinterdp.scribengin.dataflow.KafkaWireDataSetFactory;
import com.neverwinterdp.scribengin.dataflow.Operator;
import com.neverwinterdp.scribengin.dataflow.registry.DataflowRegistry;
import com.neverwinterdp.scribengin.shell.ScribenginShell;
import com.neverwinterdp.storage.es.ESStorageConfig;
import com.neverwinterdp.storage.hdfs.HDFSStorageConfig;
import com.neverwinterdp.storage.kafka.KafkaStorageConfig;
import com.neverwinterdp.storage.nulldev.NullDevStorageConfig;
import com.neverwinterdp.util.JSONSerializer;
import com.neverwinterdp.vm.client.VMClient;

public class WebEventRouterLauncher {
  WebEventRouterConfig config = new WebEventRouterConfig();
  
  public WebEventRouterLauncher(String[] args) {
    new JCommander(config, args);
  }
  
  public void runWebEventGenerator() throws Exception {
    WebEventGenerator generator = new WebEventGenerator();
    generator.runWebEventGenerator(config.zkConnect, config.dataflowInputTopic, config.generatorNumOfWebEvents);
  }
  
  public Dataflow<WebEvent, WebEvent> buildDataflow() {
    Dataflow<WebEvent, WebEvent> dfl = new Dataflow<>(config.dataflowId);
    dfl.
      useWireDataSetFactory(new KafkaWireDataSetFactory(config.zkConnect)).
      setDefaultParallelism(config.dataflowDefaultParallelism).
      setDefaultReplication(config.dataflowDefaultReplication).
      setTrackingWindowSize(config.dataflowTrackingWindowSize).
      setSlidingWindowSize(config.dataflowSlidingWindowSize);
    
    dfl.getWorkerDescriptor().setNumOfInstances(config.dataflowNumOfWorker);
    dfl.getWorkerDescriptor().setNumOfExecutor(config.dataflowNumOfExecutorPerWorker);
    
    KafkaDataSet<WebEvent> inputDs = dfl.createInput(new KafkaStorageConfig("input", config.zkConnect, config.dataflowInputTopic));
    
    //Define our output sink - set name, ZK host:port, and output topic name
    DataSet<WebEvent> kafkaOutputDs = 
        dfl.createOutput(new KafkaStorageConfig("output.kafka", config.zkConnect, config.dataflowOutputKafkaTopic));
    
    DataSet<WebEvent> hdfsOutputDs = 
        dfl.createOutput(new HDFSStorageConfig("web-event-hdfs-archive", config.dataflowOutputHdfsLocation));
    
    ESStorageConfig esStorageConfig = 
        new ESStorageConfig("web-event-es-index", config.dataflowOutputESIndex, config.dataflowOutputESAddresses, WebEvent.class);
    DataSet<WebEvent> esOutputDs    = dfl.createOutput(esStorageConfig);
    
    DataSet<WebEvent> nullDevOutputDs = dfl.createOutput(new NullDevStorageConfig());
    
    Operator<WebEvent, WebEvent> routerOp  = dfl.createOperator("router",  WebEventRouterOperator.class);
    Operator<WebEvent, WebEvent> archiveOp = dfl.createOperator("archive", WebEventPersisterOperator.class);
    Operator<WebEvent, WebEvent> junkOp    = dfl.createOperator("junk",    WebEventPersisterOperator.class);

    inputDs.
      useRawReader().
      connect(routerOp);
    
    routerOp.
      connect(junkOp).
      connect(archiveOp);
    
    junkOp.connect(nullDevOutputDs);
    
    archiveOp.
      connect(kafkaOutputDs).
      connect(hdfsOutputDs).
      connect(esOutputDs);
    return dfl;
  }
  
  /**
   * The logic to submit the dataflow
   * @throws Exception
   */
  public void submitDataflow(ScribenginShell shell) throws Exception {
    //Upload our app to HDFS
    VMClient vmClient = shell.getScribenginClient().getVMClient();
    vmClient.uploadApp(config.localAppHome, config.dfsAppHome);
    
    Dataflow<WebEvent, WebEvent> dfl = buildDataflow();
    //Get the dataflow's descriptor
    DataflowDescriptor dflDescriptor = dfl.buildDataflowDescriptor();
    //Output the descriptor in human-readable JSON
    System.out.println(JSONSerializer.INSTANCE.toString(dflDescriptor));

    //Ensure all your sources and sinks are up and running first, then...

    //Submit the dataflow and wait until it starts running
    DataflowSubmitter submitter = new DataflowSubmitter(shell.getScribenginClient(), dfl);
    submitter.submit().waitForDataflowRunning(60000);
  }
  
  public void runMonitor(ScribenginShell shell) throws Exception {
    ScribenginClient sclient = shell.getScribenginClient();
    DataflowClient dflClient = sclient.getDataflowClient(config.dataflowId);
    DataflowRegistry dflRegistry = dflClient.getDataflowRegistry();
    
    while(true) {
      TrackingWindowReport report = dflRegistry.getMessageTrackingRegistry().getReport();
      shell.execute("dataflow info --dataflow-id " + config.dataflowId + " --show-tasks --show-history-workers ");
      
      System.out.println(
          "Monitor: " +
          "input web events = " + config.generatorNumOfWebEvents + 
          ", tracking count = " + report.getTrackingCount() +
          ", duplicated = " + report.getTrackingDuplicatedCount());
      if(config.generatorNumOfWebEvents <= report.getTrackingCount()) {
        break;
      }
      
      Thread.sleep(10000);
    }
    
    System.err.println("Monitor: Detect all the input messages are processed, call stop dataflow");
    shell.execute("dataflow stop --dataflow-id " + config.dataflowId);
    shell.execute("dataflow info --dataflow-id " + config.dataflowId + " --show-tasks --show-history-workers");
  }
}
