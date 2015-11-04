package com.neverwinterdp.scribengin.storage.kafka;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.neverwinterdp.kafka.KafkaClient;
import com.neverwinterdp.kafka.tool.server.KafkaCluster;
import com.neverwinterdp.scribengin.storage.Record;
import com.neverwinterdp.scribengin.storage.kafka.sink.KafkaSink;
import com.neverwinterdp.scribengin.storage.kafka.source.KafkaSourcePartition;
import com.neverwinterdp.scribengin.storage.sink.SinkPartitionStream;
import com.neverwinterdp.scribengin.storage.sink.SinkPartitionStreamWriter;
import com.neverwinterdp.scribengin.storage.source.SourcePartitionStream;
import com.neverwinterdp.scribengin.storage.source.SourcePartitionStreamReader;

public class SinkSourceUnitTest {
  static {
    System.setProperty("log4j.configuration", "file:src/test/resources/test-log4j.properties");
  }

  private KafkaCluster cluster;

  @Before
  public void setUp() throws Exception {
    cluster = new KafkaCluster("./build/cluster", 1, 1);
    cluster.setNumOfPartition(5);
    cluster.start();
    Thread.sleep(3000);
  }
  
  @After
  public void tearDown() throws Exception {
    cluster.shutdown();
  }

  @Test
  public void testKafkaSource() throws Exception {
    String zkConnect = cluster.getZKConnect();
    System.out.println("zkConnect = " + zkConnect);
    String TOPIC = "hello.topic" ;
    KafkaClient kafkaClient = new KafkaClient("KafkaClient", zkConnect);
    KafkaStorage storage = new KafkaStorage(kafkaClient, "hello", TOPIC);
    KafkaSink sink = (KafkaSink) storage.getSink();
    
    SinkPartitionStream stream = sink.newStream();
    SinkPartitionStreamWriter writer = stream.getWriter();
    for(int i = 0; i < 10; i++) {
      String hello = "Hello " + i ;
      Record dataflowMessage = new Record("key-" + i, hello.getBytes());
      writer.append(dataflowMessage);
    }
    writer.close();
    
    KafkaSourcePartition source = new KafkaSourcePartition(kafkaClient, "hello", TOPIC);
    SourcePartitionStream[] streams = source.getPartitionStreams();
    Assert.assertEquals(5, streams.length);
    for(int i = 0; i < streams.length; i++) {
      System.out.println("Stream id: " + streams[i].getDescriptor().getPartitionStreamId());
      SourcePartitionStreamReader reader = streams[i].getReader("kafka");
      Record dataflowMessage = null;
      while((dataflowMessage = reader.next(1000)) != null) {
        System.out.println("Record: " + new String(dataflowMessage.getData()));
      }
    }
  }
}
