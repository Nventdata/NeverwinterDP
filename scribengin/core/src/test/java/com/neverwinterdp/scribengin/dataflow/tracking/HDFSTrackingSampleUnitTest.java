package com.neverwinterdp.scribengin.dataflow.tracking;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HDFSTrackingSampleUnitTest  {
  TrackingSampleRunner trackingSampleRunner = new TrackingSampleRunner();
  
  @Before
  public void setup() throws Exception {
    trackingSampleRunner.dataflowMaxRuntime = 90000;
    trackingSampleRunner.setup();
  }
  
  @After
  public void teardown() throws Exception {
    trackingSampleRunner.teardown();
  }
  
  @Test
  public void testTrackingSample() throws Exception {
    trackingSampleRunner.submitVMTMGenrator();
    trackingSampleRunner.submitHDFSTMDataflow();
    trackingSampleRunner.submitHDFSVMTMValidator();
    trackingSampleRunner.runMonitor();
  }
}