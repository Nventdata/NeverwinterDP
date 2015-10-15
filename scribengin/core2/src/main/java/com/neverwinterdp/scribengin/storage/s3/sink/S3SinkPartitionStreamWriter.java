package com.neverwinterdp.scribengin.storage.s3.sink;

import java.io.IOException;
import java.util.UUID;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.neverwinterdp.scribengin.storage.Record;
import com.neverwinterdp.scribengin.storage.s3.S3Folder;
import com.neverwinterdp.scribengin.storage.s3.S3ObjectWriter;
import com.neverwinterdp.scribengin.storage.sink.SinkPartitionStreamWriter;
import com.neverwinterdp.util.JSONSerializer;

public class S3SinkPartitionStreamWriter implements SinkPartitionStreamWriter {
  static private final int TIMEOUT = 5 * 60 * 1000;
  
  private S3Folder       streamS3Folder;
  private SegmentWriter  currentWriter ;

  public S3SinkPartitionStreamWriter(S3Folder streamS3Folder) throws IOException {
    this.streamS3Folder = streamS3Folder;
  }
  
  @Override
  public void append(Record dataflowMessage) throws Exception {
    if(currentWriter == null) currentWriter = new SegmentWriter(streamS3Folder);
    currentWriter.append(dataflowMessage);
  }

  @Override
  public void prepareCommit() throws Exception {
    if(currentWriter == null) return ;
    currentWriter.prepareCommit();
  }

  @Override
  public void completeCommit() throws Exception {
    if(currentWriter == null) return ;
    currentWriter.completeCommit(); 
    currentWriter = null ;
  }

  @Override
  public void commit() throws Exception {
    if(currentWriter == null) return ;  
    currentWriter.prepareCommit();
    currentWriter.completeCommit();
  }

  @Override
  public void rollback() throws Exception {
    if(currentWriter == null) return;
    currentWriter.rollback() ;
    currentWriter = null ;
  }

  @Override
  public void close() throws Exception {
    if(currentWriter != null) {
      currentWriter.close();
      currentWriter = null ;
    }
  }
  
  static public class SegmentWriter {
    private String         currentSegmentName;
    private S3ObjectWriter currentWriter ;
    private S3Folder streamS3Folder ;
    
    public SegmentWriter(S3Folder streamS3Folder) throws IOException {
      this.streamS3Folder = streamS3Folder ;
    }
    
    
    public void append(Record dataflowMessage) throws Exception {
      if(currentWriter == null) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.addUserMetadata("transaction", "prepare");
        currentSegmentName = "segment-" + UUID.randomUUID().toString();
        currentWriter = streamS3Folder.createObjectWriter(currentSegmentName, metadata);
      }
      byte[] bytes = JSONSerializer.INSTANCE.toBytes(dataflowMessage);
      currentWriter.write(bytes);
    }

    public void prepareCommit() throws Exception {
      if(currentWriter == null) return ;
      currentWriter.waitAndClose(TIMEOUT);
    }

    public void completeCommit() throws Exception {
      if(currentWriter == null) return ;
      ObjectMetadata metadata = currentWriter.getObjectMetadata();
      metadata.addUserMetadata("transaction", "complete");
      streamS3Folder.updateObjectMetadata(currentSegmentName, metadata);
      currentWriter = null;
    }

    public void commit() throws Exception {
      prepareCommit();
      completeCommit();
    }

    public void rollback() throws Exception {
      if(currentWriter == null) return;
      currentWriter.forceClose() ;
      streamS3Folder.deleteObject(currentSegmentName);
      currentWriter = null ;
    }
    
    public void close() throws Exception {
      if(currentWriter != null) rollback() ;
    }
  }
}