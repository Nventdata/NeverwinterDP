package com.neverwinterdp.scribengin.storage.s3.sink;

import java.util.LinkedHashMap;
import java.util.List;

import com.neverwinterdp.scribengin.storage.StorageConfig;
import com.neverwinterdp.scribengin.storage.PartitionStreamConfig;
import com.neverwinterdp.scribengin.storage.s3.S3Client;
import com.neverwinterdp.scribengin.storage.s3.S3Folder;
import com.neverwinterdp.scribengin.storage.s3.S3Storage;
import com.neverwinterdp.scribengin.storage.sink.Sink;
import com.neverwinterdp.scribengin.storage.sink.SinkPartitionStream;

public class S3Sink implements Sink {
  private S3Storage storage ;
  private S3Client s3Client ;
  private S3Folder sinkFolder;
  private LinkedHashMap<Integer, S3SinkPartitionStream> streams = new LinkedHashMap<>();
  private int streamIdTracker = 0;
  
  public S3Sink(StorageConfig descriptor) {
    this.storage = new S3Storage(descriptor);
    this.s3Client = storage.getS3Client();
    init();
  }

  public S3Sink(S3Client s3Client, StorageConfig descriptor) {
    this.storage = new S3Storage(descriptor);
    this.s3Client = s3Client;
    init();
  }
  
  private void init() {
    String bucketName = storage.getBucketName();
    String storageFolder = storage.getStorageFolder();
    if (!s3Client.hasKey(bucketName, storageFolder)) {
      sinkFolder = s3Client.createS3Folder(bucketName, storageFolder);
    } else {
      sinkFolder = s3Client.getS3Folder(bucketName, storageFolder);
    }
    
    List<String> streamNames = sinkFolder.getChildrenNames();
    for (String streamName : streamNames) {
      PartitionStreamConfig pConfig = storage.createPartitionConfig(streamName);
      S3SinkPartitionStream stream = 
          new S3SinkPartitionStream(sinkFolder, storage.getStorageDescriptor(), pConfig);
      streams.put(stream.getPartitionStreamId(), stream);
      if (streamIdTracker < stream.getPartitionStreamId()) {
        streamIdTracker = stream.getPartitionStreamId();
      }
    }
  }

  public S3Folder getSinkFolder() { return this.sinkFolder; }

  @Override
  public StorageConfig getDescriptor() { return storage.getStorageDescriptor(); }

  @Override
  synchronized public SinkPartitionStream getPartitionStream(PartitionStreamConfig descriptor) throws Exception {
    return streams.get(descriptor.getPartitionStreamId());
  }

  @Override
  synchronized public SinkPartitionStream getParitionStream(int partitionId) throws Exception {
    return streams.get(partitionId);
  }

  
  @Override
  synchronized public SinkPartitionStream[] getPartitionStreams() {
    SinkPartitionStream[] array = new SinkPartitionStream[streams.size()];
    return streams.values().toArray(array);
  }

  //TODO: Should consider a sort of transaction to make the operation reliable
  @Override
  synchronized public void delete(SinkPartitionStream stream) throws Exception {
    SinkPartitionStream found = streams.remove(stream.getPartitionStreamId());
    if (found != null) {
      found.delete();
    }
  }
  

  @Override
  synchronized public SinkPartitionStream newStream() throws Exception {
    int streamId = streamIdTracker++;
    PartitionStreamConfig pConfig = storage.createPartitionConfig(streamId);
    S3SinkPartitionStream stream = 
      new S3SinkPartitionStream(sinkFolder, storage.getStorageDescriptor(), pConfig);
    streams.put(streamId, stream);
    return stream;
  }

  @Override
  public void close() throws Exception {
  }
}
