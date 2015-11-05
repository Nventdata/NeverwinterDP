package com.neverwinterdp.scribengin.storage.hdfs.sink;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.fs.FileSystem;

import com.neverwinterdp.scribengin.storage.PartitionStreamConfig;
import com.neverwinterdp.scribengin.storage.StorageConfig;
import com.neverwinterdp.scribengin.storage.sink.Sink;
import com.neverwinterdp.scribengin.storage.sink.SinkPartitionStream;

public class HDFSSink implements Sink {
  private FileSystem fs;
  private StorageConfig storageConfig;
  
  public HDFSSink(FileSystem fs, String location) throws FileNotFoundException, IllegalArgumentException, IOException {
    this(fs, new StorageConfig("HDFS", location));
  }
  
  public HDFSSink(FileSystem fs, StorageConfig sConfig) throws FileNotFoundException, IllegalArgumentException, IOException {
    this.fs = fs;
    this.storageConfig = sConfig;
  }
  
  public StorageConfig getDescriptor() { return this.storageConfig; }
  
  public List<PartitionStreamConfig> getPartitionStreamConfigs() throws Exception {
    int numOfPartitionStream = storageConfig.getPartitionStream();
    List<PartitionStreamConfig> holder = new ArrayList<>();
    for(int i = 0; i < numOfPartitionStream; i++) {
      PartitionStreamConfig config = new PartitionStreamConfig(i, null);
      holder.add(config);
    }
    return holder;
  }
  
  public SinkPartitionStream  getPartitionStream(PartitionStreamConfig config) throws Exception {
    return getParitionStream(config.getPartitionStreamId());
  }
  
  public SinkPartitionStream  getParitionStream(int partitionId) throws Exception {
    String location = storageConfig.getLocation() + "/partition-stream-" + partitionId;
    PartitionStreamConfig pConfig = new PartitionStreamConfig(partitionId, location) ;
    HDFSSinkPartitionStream stream = new HDFSSinkPartitionStream(fs, pConfig);
    return stream ;
  }
  
  synchronized public SinkPartitionStream[] getPartitionStreams() throws Exception {
    int numOfPartitionStream = storageConfig.getPartitionStream();
    SinkPartitionStream[] stream = new SinkPartitionStream[numOfPartitionStream];
    for(int i = 0; i < numOfPartitionStream; i++) {
      stream[i] = getParitionStream(i);
    }
    return stream;
  }

  @Override
  public void close() throws Exception  { 
  }
  
  public void fsCheck() throws Exception {
  }
}