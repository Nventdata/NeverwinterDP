package com.neverwinterdp.scribengin.storage.kafka.source;

import java.util.ArrayList;
import java.util.List;

import com.neverwinterdp.kafka.KafkaClient;
import com.neverwinterdp.scribengin.storage.StorageConfig;
import com.neverwinterdp.scribengin.storage.source.Source;
import com.neverwinterdp.scribengin.storage.source.SourcePartition;

public class KafkaSource implements Source {
  private KafkaClient          kafkaClient;
  private StorageConfig        storageConfig;
  private KafkaSourcePartition partition ;
  
  public KafkaSource(KafkaClient kafkaClient, String name, String topic) throws Exception {
    this(kafkaClient, createStorageConfig(name, topic, kafkaClient.getZkConnects(), null));
  }
  
  public KafkaSource(KafkaClient kafkaClient, StorageConfig sconfig) throws Exception {
    this.kafkaClient = kafkaClient;
    this.storageConfig = sconfig;
    this.partition = new KafkaSourcePartition(kafkaClient, storageConfig);
  }
  
  @Override
  public StorageConfig getStorageConfig() { return storageConfig; }

  @Override
  public SourcePartition getLatestSourcePartition() throws Exception { return partition; }

  @Override
  public List<SourcePartition> getSourcePartitions() throws Exception {
    List<SourcePartition> holder = new ArrayList<>();
    holder.add(partition);
    return holder;
  }
  
  static public StorageConfig createStorageConfig(String name, String topic, String zkConnect, String reader) {
    StorageConfig descriptor = new StorageConfig("kafka");
    descriptor.attribute("name", name);
    descriptor.attribute("topic", topic);
    descriptor.attribute("zk.connect", zkConnect);
    if(reader != null) {
      descriptor.attribute("reader", reader);
    } else {
      descriptor.attribute("reader", "record");
    }
    return descriptor;
  }
}
