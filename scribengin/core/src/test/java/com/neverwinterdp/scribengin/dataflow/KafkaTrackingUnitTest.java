package com.neverwinterdp.scribengin.dataflow;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.neverwinterdp.scribengin.LocalScribenginCluster;
import com.neverwinterdp.scribengin.dataflow.tracking.TrackingDataflowBuilder;
import com.neverwinterdp.scribengin.shell.ScribenginShell;
import com.neverwinterdp.util.JSONSerializer;
import com.neverwinterdp.vm.VMConfig;
import com.neverwinterdp.vm.client.VMClient;
import com.neverwinterdp.vm.client.VMSubmitter;

public class KafkaTrackingUnitTest  {
  LocalScribenginCluster localScribenginCluster ;
  ScribenginShell shell;
  
  @Before
  public void setup() throws Exception {
    String BASE_DIR = "build/working";
    System.setProperty("app.home",   BASE_DIR + "/scribengin");
    System.setProperty("vm.app.dir", BASE_DIR + "/scribengin");
    
    localScribenginCluster = new LocalScribenginCluster(BASE_DIR) ;
    localScribenginCluster.clean(); 
    localScribenginCluster.useLog4jConfig("classpath:scribengin/log4j/vm-log4j.properties");  
    localScribenginCluster.start();
    
    shell = localScribenginCluster.getShell();
  }
  
  @After
  public void teardown() throws Exception {
    localScribenginCluster.shutdown();
  }
  
  @Test
  public void testTracking() throws Exception {
    VMClient vmClient = shell.getVMClient();
    String dfsAppHome = "";
    
    TrackingDataflowBuilder dflBuilder = new TrackingDataflowBuilder("tracking");
    dflBuilder.getTrackingConfig().setNumOfMessagePerChunk(10000);
    dflBuilder.setMaxRuntime(120000);
    
    VMConfig vmGeneratorConfig = dflBuilder.buildVMTMGeneratorKafka();
    new VMSubmitter(vmClient, dfsAppHome, vmGeneratorConfig).submit().waitForRunning(30000);
    
    Dataflow dfl = dflBuilder.buildDataflow();
    dfl.setDefaultParallelism(5);
    dfl.setDefaultReplication(1);
    DataflowDescriptor dflDescriptor = dfl.buildDataflowDescriptor();
    System.out.println(JSONSerializer.INSTANCE.toString(dflDescriptor));
    
    try {
      new DataflowSubmitter(shell.getScribenginClient(), dfl).submit().waitForDataflowRunning(60000);
    } catch (Exception ex) {
      shell.execute("registry dump");
      throw ex;
    }
    VMConfig vmValidatorConfig = dflBuilder.buildKafkaVMTMValidator();
    new VMSubmitter(vmClient, dfsAppHome, vmValidatorConfig).submit().waitForRunning(30000);
    
    dflBuilder.runMonitor(shell);
    shell.execute("registry dump");
  }
}