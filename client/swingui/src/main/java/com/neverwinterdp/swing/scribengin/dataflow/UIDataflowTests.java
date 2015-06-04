package com.neverwinterdp.swing.scribengin.dataflow;

import java.awt.Dimension;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;

import com.neverwinterdp.swing.UILifecycle;
import com.neverwinterdp.swing.scribengin.dataflow.test.DataflowTestRunner;
import com.neverwinterdp.swing.widget.GridLayoutPanel;

@SuppressWarnings("serial")
public class UIDataflowTests extends GridLayoutPanel implements UILifecycle {
  public UIDataflowTests() {
  }
  
  @Override
  public void onInit() throws Exception {
  }

  @Override
  public void onDestroy() throws Exception {
  }

  @Override
  public void onActivate() throws Exception {
    removeAll();
    Action kafkaToKafkaTest = new AbstractAction("Dataflow Kafka To Kafka Test") {
      @Override
      public void actionPerformed(ActionEvent e) {
        new DataflowTestRunner.DataflowKafkaToKakaTestRunner().start();
      }
    };
    
    Action hdfsToHdfsTest = new AbstractAction("Dataflow Hdfs To Hdfs Test") {
      @Override
      public void actionPerformed(ActionEvent e) {
        new DataflowTestRunner.DataflowHDFSToHDFSTestRunner().start();
      }
    };
    
    Action dataflowServerFailureTest = new AbstractAction("Dataflow Worker Failure Test") {
      @Override
      public void actionPerformed(ActionEvent e) {
        new DataflowTestRunner.DataflowWorkerFailureTestRunner().start();
      }
    };
    
    Action dataflowStartStopResumeTest = new AbstractAction("Dataflow Start/Stop/Resume Test") {
      @Override
      public void actionPerformed(ActionEvent e) {
        new DataflowTestRunner.DataflowStartStopResumtTestRunner().start();
      }
    };
    
    Action logSampleTest = new AbstractAction("Log Sample Test") {
      @Override
      public void actionPerformed(ActionEvent e) {
        new DataflowTestRunner.LogSampleTestRunner().start();
      }
    };
    
    addCells(kafkaToKafkaTest, hdfsToHdfsTest, dataflowServerFailureTest, dataflowStartStopResumeTest, logSampleTest);
    makeGrid(1);
  }

  @Override
  public void onDeactivate() throws Exception {
    removeAll();
  }
}
