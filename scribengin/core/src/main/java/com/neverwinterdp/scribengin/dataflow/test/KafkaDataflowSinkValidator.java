package com.neverwinterdp.scribengin.dataflow.test;

import java.io.IOException;

import com.neverwinterdp.kafka.tool.KafkaMessageCheckTool;
import com.neverwinterdp.kafka.tool.KafkaTopicReport;
import com.neverwinterdp.scribengin.ScribenginClient;
import com.neverwinterdp.scribengin.dataflow.test.DataflowTestReport.DataflowSinkValidatorReport;
import com.neverwinterdp.scribengin.storage.StorageDescriptor;
import com.neverwinterdp.scribengin.storage.kafka.source.KafkaSource;

public class KafkaDataflowSinkValidator extends DataflowSinkValidator {
  private KafkaMessageCheckTool kafkaMessageCheckTool;
  private String zkConnect ;
  
  @Override
  public StorageDescriptor getSinkDescriptor() {
    StorageDescriptor sink = 
      KafkaSource.createStorageDescriptor("KafkaDataflowSinkValidator", sinkName, zkConnect, null);
    return sink;
  }

  @Override
  public void init(ScribenginClient scribenginClient) {
    zkConnect = scribenginClient.getRegistry().getRegistryConfig().getConnect();
  }
  
  @Override
  public void run() {
    kafkaMessageCheckTool = createKafkaMessageCheckTool();
    kafkaMessageCheckTool.run();
  }

  @Override
  public void runInBackground() {
    kafkaMessageCheckTool = createKafkaMessageCheckTool();
    kafkaMessageCheckTool.runAsDeamon();
  }

  @Override
  public boolean waitForTermination() throws InterruptedException {
    return kafkaMessageCheckTool.waitForTermination();
  }

  @Override
  public boolean waitForTermination(long timeout) throws InterruptedException {
    return kafkaMessageCheckTool.waitForTermination(timeout);
  }
  
  @Override
  public void populate(DataflowTestReport report) {
    KafkaTopicReport topicReport = kafkaMessageCheckTool.getReport() ;
    DataflowSinkValidatorReport sinkReport = report.getSinkValidatorReport();
    sinkReport.setSinkName(topicReport.getTopic());
    sinkReport.setNumberOfStreams(topicReport.getNumOfPartitions());
    sinkReport.setReadCount(topicReport.getConsumerReport().getMessagesRead());
    sinkReport.setDuration(topicReport.getConsumerReport().getRunDuration());
    try {
      kafkaMessageCheckTool.getMessageTracker().dump(System.out);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  KafkaMessageCheckTool createKafkaMessageCheckTool() {
    String[] args = {
      "--topic",                  sinkName, 
      "--num-partition",          Integer.toString(5),
      "--consume-max",            Long.toString(expectRecords),
      "--consume-retries",        "5",
      "--zk-connect",             zkConnect
    };
    KafkaMessageCheckTool kafkaMessageCheckTool = new KafkaMessageCheckTool(args);
    kafkaMessageCheckTool.setMessageExtractor(new RecordMessageExtractor());
    return kafkaMessageCheckTool;
  }

  @Override
  public boolean canWaitForTermination() {
    return true;
  }
}
