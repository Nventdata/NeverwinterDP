package com.neverwinterdp.storage.hdfs;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.apache.hadoop.fs.FileSystem;

import com.neverwinterdp.registry.ErrorCode;
import com.neverwinterdp.registry.Registry;
import com.neverwinterdp.registry.RegistryException;
import com.neverwinterdp.ssm.SSMRegistry;
import com.neverwinterdp.ssm.SSMTagDescriptor;
import com.neverwinterdp.ssm.hdfs.HdfsSSM;
import com.neverwinterdp.ssm.hdfs.HdfsSSMConsistencyVerifier;
import com.neverwinterdp.storage.Storage;
import com.neverwinterdp.storage.StorageConfig;
import com.neverwinterdp.storage.hdfs.sink.HDFSSink;
import com.neverwinterdp.storage.hdfs.source.HDFSSource;

public class HDFSStorage extends Storage {
  final static public String HDFS_REGISTRY = "/storage/hdfs";
  
  private HDFSStorageRegistry storageRegistry;
  private FileSystem          fs;

  public HDFSStorage(Registry registry, FileSystem fs, StorageConfig storageConfig) throws RegistryException {
    super(storageConfig);
    this.fs = fs ;
    storageRegistry = new HDFSStorageRegistry(registry, storageConfig);
    setStorageConfig(storageRegistry.getStorageConfig());
  }

  public HDFSStorageRegistry getRegistry() { return storageRegistry; }
  
  @Override
  public void refresh() throws Exception {
  }

  @Override
  public boolean exists() throws Exception {
    return storageRegistry.exists();
  }

  @Override
  public void drop() throws RegistryException {
    storageRegistry.drop();
  }

  @Override
  public void create() throws Exception {
    if(storageRegistry.exists()) {
      throw new RegistryException(ErrorCode.NodeExists, "The storage is already initialized");
    }
    storageRegistry.create();
  }

  @Override
  public HDFSSink getSink() throws Exception { return new HDFSSink(storageRegistry, fs); }

  @Override
  public HDFSSource getSource() throws Exception { return new HDFSSource(storageRegistry, fs); }
  
  public HdfsSSM getPartition(int partitionId) throws RegistryException, IOException {
    StorageConfig sConfig = getStorageConfig();
    String pLocation = sConfig.getLocation() + "/partition-" + partitionId;
    SSMRegistry pRegistry = storageRegistry.getPartitionRegistry(partitionId);
    return new HdfsSSM(fs, pLocation, pRegistry);
  }
  
  public HdfsSSM[] getPartitions() throws RegistryException, IOException {
    StorageConfig sConfig = getStorageConfig();
    int numOfPartitionStream = sConfig.getPartitionStream();
    HdfsSSM[] partitions = new HdfsSSM[numOfPartitionStream];
    for(int i = 0; i < numOfPartitionStream; i++) {
      String pLocation = sConfig.getLocation() + "/partition-" + i;
      SSMRegistry pRegistry = storageRegistry.getPartitionRegistry(i);
      partitions[i] = new HdfsSSM(fs, pLocation, pRegistry);
    }
    return partitions;
  }
  
  public void cleanDataByTag(HDFSStorageTag tag) throws RegistryException, IOException {
    Map<Integer, SSMTagDescriptor> partitionTags = tag.getPartitionTagDescriptors();
    for(Map.Entry<Integer, SSMTagDescriptor> entry : partitionTags.entrySet()) {
      int partitionId = entry.getKey();
      HdfsSSM partition = getPartition(partitionId);
      partition.dropSegmentByTag(entry.getValue());
    }
  }
  
  public void cleanReadDataByActiveReader() throws RegistryException, IOException {
    HdfsSSM[] streams = getPartitions() ;
    for(HdfsSSM sel : streams) {
      sel.cleanReadSegmentByActiveReader();
    }
  }
  
  public void doManagement() throws RegistryException, IOException {
    HdfsSSM[] streams = getPartitions() ;
    for(HdfsSSM sel : streams) {
      sel.doManagement();
    }
  }
  
  public HDFSStorageTag findTagByRecordLastPosition(String name, String desc) throws RegistryException {
    return storageRegistry.getTagByRecordLastPosition(name, desc);
  }
  
  public HDFSStorageTag findTagByPosition(String name, String desc, long pos) throws RegistryException {
    return storageRegistry.findTagByPosition(name, desc, pos);
  }
  
  public HDFSStorageTag findTagByDateTime(String name, String desc, Date datetime) throws RegistryException {
    return storageRegistry.findTagByDateTime(name, desc, datetime);
  }
  
  public void createTag(HDFSStorageTag tag) throws RegistryException {
    storageRegistry.createTag(tag);
  }
  
  public void report(Appendable out) throws RegistryException, IOException {
    HdfsSSM[] streams = getPartitions() ;
    for(int i = 0; i < streams.length; i++) {
      HdfsSSM sel = streams[i];
      HdfsSSMConsistencyVerifier verifier = sel.getSegmentConsistencyVerifier();
      verifier.verify();
      out.append("Partition: " + i).append("\n");
      out.append("----------------------------------------------------------------------\n");
      out.append(verifier.getSegmentConsistencyTextReport()).append("\n");
    }
  }
}