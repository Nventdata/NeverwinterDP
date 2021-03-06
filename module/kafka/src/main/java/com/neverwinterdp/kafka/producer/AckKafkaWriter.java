package com.neverwinterdp.kafka.producer;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tuan Nguyen
 * @email tuan08@gmail.com
 */
public class AckKafkaWriter extends AbstractKafkaWriter {
  static private Logger LOGGER = LoggerFactory.getLogger(AckKafkaWriter.class);
  
  private Properties kafkaProperties;
  private KafkaProducer<byte[], byte[]> producer;
  private AtomicLong idTracker = new AtomicLong();
  private WaittingAckProducerRecordHolder<byte[], byte[]> waittingAckBuffer = new WaittingAckProducerRecordHolder<byte[], byte[]>();
  private ResendThread resendThread ;
  private int maxCommitTimeout = 300000;
  
  public AckKafkaWriter(String name, String kafkaBrokerUrls) {
    this(name, null, kafkaBrokerUrls);
  }

  public AckKafkaWriter(String name, Map<String, String> props, String kafkaBrokerUrls) {
    super(name);
    Properties kafkaProps = new Properties();
    kafkaProps.setProperty(ProducerConfig.CLIENT_ID_CONFIG, name);
    kafkaProps.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrls);
    kafkaProps.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    kafkaProps.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   ByteArraySerializer.class.getName());
    
    kafkaProps.setProperty(ProducerConfig.ACKS_CONFIG, "all");
    kafkaProps.setProperty(ProducerConfig.TIMEOUT_CONFIG, "60000");

    kafkaProps.setProperty(ProducerConfig.RETRIES_CONFIG, "5");
    kafkaProps.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
    kafkaProps.setProperty(ProducerConfig.BLOCK_ON_BUFFER_FULL_CONFIG, "true");
    
    kafkaProps.setProperty(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "500");
    kafkaProps.setProperty(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, "10");
    
    kafkaProps.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
    
    if (props != null) {
      kafkaProps.putAll(props);
    }
    this.kafkaProperties = kafkaProps;
    reconnect();
  }

  public void reconnect() {
    if (producer != null) producer.close();
    producer = new KafkaProducer<byte[], byte[]>(kafkaProperties);
  }
  
  public void send(String topic, int partition, byte[] key, byte[] message, Callback callback, long timeout) throws Exception  {
    ProducerRecord<byte[], byte[]> record = null;
    if(partition >= 0) record = new ProducerRecord<byte[], byte[]>(topic, partition, key, message);
    else record = new ProducerRecord<byte[], byte[]>(topic, key, message);
    
    long id = idTracker.incrementAndGet();
    WaittingAckProducerRecord<byte[], byte[]> ackRecord = new WaittingAckProducerRecord<byte[], byte[]>(id, record, callback);
    waittingAckBuffer.add(ackRecord, timeout);
    AckCallback ackCallback = new AckCallback(id);
    producer.send(record, ackCallback);
  }
  
  void triggerResendThread() {
    if(resendThread == null || !resendThread.isAlive()) {
      resendThread = new ResendThread();
      resendThread.start();
    }
  }
  
  public void waitAndClose(long timeout)  {
    try {
      int waitTime = 0;
      while(waitTime < timeout && waittingAckBuffer.size() > 0) {
        Thread.sleep(100);
        waitTime += 100;
      }
    } catch (InterruptedException e) {
    }
    producer.close(); 
  }
  
  public void waitForAcks(long timeout)  throws KafkaException {
    try {
      int waitTime = 0;
      while(waitTime < timeout && waittingAckBuffer.size() > 0) {
        Thread.sleep(100);
        waitTime += 100;
      }
    } catch (InterruptedException e) {
    }
    if(waittingAckBuffer.size() > 0) {
      throw new KafkaException("There are still " + waittingAckBuffer.size() + " messages in the waiting list") ;
    }
  }
  
  public void commit() throws Exception {
    waittingAckBuffer.waitForEmptyBuffer(maxCommitTimeout);
  }
  
  public void close() throws InterruptedException { 
    if(resendThread != null && resendThread.isAlive()) {
      resendThread.waitForTermination(maxCommitTimeout);
    }
    if(producer == null) return;
    producer.close(); 
    producer = null ;
  }
  
  public void foceClose() { 
    if(resendThread != null && resendThread.isAlive()) {
      resendThread.interrupt();
    }
    waittingAckBuffer.clear();
    producer.close(); 
    producer = null ;
  }
  
  public class ResendThread extends Thread {
    public void run() {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        return;
      }
      List<WaittingAckProducerRecord<byte[],byte[]>> needToResendRecords =  waittingAckBuffer.getNeedToResendRecords();
      while(needToResendRecords.size() > 0) {
        for(WaittingAckProducerRecord<byte[], byte[]> sel : needToResendRecords) {
          AckCallback ackCallback = new AckCallback(sel.getId());
          producer.send(sel.getProducerRecord(), ackCallback);
          sel.setNeedToResend(false);
        }
        System.err.println("Resend " + needToResendRecords.size());
        needToResendRecords =  waittingAckBuffer.getNeedToResendRecords();
      }
    }
    
    synchronized void notifyTermination() {
      notifyAll() ;
    }
    
    synchronized void waitForTermination(long timeout) throws InterruptedException {
      wait(timeout);
    }
  }
  
  public class AckCallback implements Callback {
    private long recordId ;
    
    AckCallback(long recordId) {
      this.recordId = recordId ;
    }
    
    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
      try {
        if(exception != null) {
          WaittingAckProducerRecord<byte[],byte[]> ackRecord = waittingAckBuffer.onFail(recordId);
          if(ackRecord.getCallback() != null) {
            ackRecord.getCallback().onCompletion(metadata, exception);
          }
          triggerResendThread();
        } else {
          //remove the the successfully sent record
          WaittingAckProducerRecord<byte[],byte[]> ackRecord = waittingAckBuffer.onSuccess(recordId);
          if(ackRecord.getCallback() != null) {
            ackRecord.getCallback().onCompletion(metadata, exception);
          }
        }
      } catch(Exception ex) {
        LOGGER.error("Error handling ack buffer", ex);
      }
    }
  }
}