package com.neverwinterdp.scribengin.dataflow.sample;


import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.util.Properties;

import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import com.neverwinterdp.scribengin.ScribenginClient;
import com.neverwinterdp.scribengin.dataflow.tool.tracking.VMTMGeneratorKafkaApp;
import com.neverwinterdp.scribengin.dataflow.tool.tracking.VMTMValidatorKafkaApp;
import com.neverwinterdp.scribengin.shell.ScribenginShell;
import com.neverwinterdp.scribengin.tool.EmbededVMClusterBuilder;
import com.neverwinterdp.scribengin.tool.ScribenginClusterBuilder;
import com.neverwinterdp.util.io.FileUtil;
import com.neverwinterdp.util.io.IOUtil;
import com.neverwinterdp.util.log.LoggerFactory;

public class KafkaTrackingSampleRunner  {
  String dataflowId           = "tracking-dataflow";
  int    numOfMessagePerChunk = 1000;
  long   dataflowMaxRuntime   = 90000;
  
  ScribenginClusterBuilder clusterBuilder ;
  Node esNode ;
  ScribenginShell shell;
  
  public void setup() throws Exception {
    FileUtil.removeIfExist("build/hdfs", false);
    FileUtil.removeIfExist("build/data", false);
    FileUtil.removeIfExist("build/logs", false);
    FileUtil.removeIfExist("build/elasticsearch", false);
    FileUtil.removeIfExist("build/cluster", false);
    FileUtil.removeIfExist("build/scribengin", false);
    
    System.setProperty("vm.app.dir", "build/scribengin");
    Properties log4jProps = new Properties() ;
    log4jProps.load(IOUtil.loadRes("classpath:scribengin/log4j/vm-log4j.properties"));
    log4jProps.setProperty("log4j.rootLogger", "INFO, file");
    LoggerFactory.log4jConfigure(log4jProps);
    
    NodeBuilder nb = nodeBuilder();
    nb.getSettings().put("cluster.name",       "neverwinterdp");
    nb.getSettings().put("path.data",          "build/elasticsearch/data");
    nb.getSettings().put("node.name",          "elasticsearch-1");
    nb.getSettings().put("transport.tcp.port", "9300");
    esNode = nb.node();
    
    clusterBuilder = new ScribenginClusterBuilder(new EmbededVMClusterBuilder()) ;
    clusterBuilder.clean(); 
    clusterBuilder.startVMMasters();
    
    ScribenginClient scribenginClient = clusterBuilder.getScribenginClient() ;
    shell = new ScribenginShell(scribenginClient);
  }
  
  public void teardown() throws Exception {
    clusterBuilder.shutdown();
    esNode.close();
  }
  
  public void runDataflow() throws Exception {
    String REPORT_PATH = "/applications/tracking-sample/reports";
    String logGeneratorSubmitCommand = 
        "vm submit " +
        "  --dfs-app-home /applications/tracking-sample" +
        "  --registry-connect 127.0.0.1:2181" +
        "  --registry-db-domain /NeverwinterDP" +
        "  --registry-implementation com.neverwinterdp.registry.zk.RegistryImpl" + 
        "  --name vm-tracking-generator-1 --role vm-tm-generator" + 
        "  --vm-application " + VMTMGeneratorKafkaApp.class.getName() + 
        
        "  --prop:tracking.report-path=" + REPORT_PATH +
        "  --prop:tracking.num-of-writer=3" +
        "  --prop:tracking.num-of-chunk=10" +
        "  --prop:tracking.num-of-message-per-chunk=" + numOfMessagePerChunk +
        "  --prop:tracking.break-in-period=10" +
        "  --prop:tracking.message-size=512" +
         
        "  --prop:kafka.zk-connects=127.0.0.1:2181" +
        "  --prop:kafka.topic=tracking.input" +
        "  --prop:kafka.num-of-partition=5" +
        "  --prop:kafka.replication=1" ;
    shell.execute(logGeneratorSubmitCommand);
    
    String dataflowChainSubmitCommand = 
        "dataflow submit " + 
        "  --dataflow-config src/test/resources/kafka-tracking-dataflow.json" +
        "  --dataflow-id " + dataflowId + " --dataflow-max-runtime " + dataflowMaxRuntime;
    shell.execute(dataflowChainSubmitCommand);
    
    String logValidatorSubmitCommand = 
      "vm submit " +
      "  --dfs-app-home /applications/tracking-sample" +
      "  --registry-connect 127.0.0.1:2181" +
      "  --registry-db-domain /NeverwinterDP" +
      "  --registry-implementation com.neverwinterdp.registry.zk.RegistryImpl" + 
      "  --name vm-tracking-validator-1 --role tracking-validator" + 
      "  --vm-application " + VMTMValidatorKafkaApp.class.getName() + 
      
      "  --prop:tracking.report-path=" + REPORT_PATH +
      "  --prop:tracking.num-of-reader=3"  +
      "  --prop:tracking.expect-num-of-message-per-chunk=" + numOfMessagePerChunk +
      "  --prop:tracking.max-runtime=120000"  +
      "  --prop:kafka.zk-connects=127.0.0.1:2181"  +
      "  --prop:kafka.topic=tracking.aggregate"  +
      "  --prop:kafka.message-wait-timeout=30000" ;
    
    shell.execute(logValidatorSubmitCommand);

    shell.execute(
      "plugin com.neverwinterdp.scribengin.dataflow.tool.tracking.TrackingMonitor" +
      "  --dataflow-id " + dataflowId +
      "  --report-path " + REPORT_PATH + " --max-runtime " + dataflowMaxRuntime +"  --print-period 10000"
    );
  }
}