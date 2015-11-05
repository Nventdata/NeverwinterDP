package com.neverwinterdp.scribengin.storage.hdfs.sink;

import java.io.IOException;

import com.neverwinterdp.scribengin.storage.Record;
import com.neverwinterdp.scribengin.storage.PartitionStreamConfig;
import com.neverwinterdp.scribengin.storage.hdfs.Segment;
import com.neverwinterdp.scribengin.storage.hdfs.SegmentStorage;
import com.neverwinterdp.scribengin.storage.hdfs.SegmentStorageWriter;
import com.neverwinterdp.scribengin.storage.sink.SinkPartitionStreamWriter;

public class HDFSSinkPartitionStreamWriter extends SegmentStorageWriter<Record> implements SinkPartitionStreamWriter {
  private PartitionStreamConfig descriptor ;
  private long             smallDataSizeAccumulate  = 0;
  private long             mediumDataSizeAccumulate = 0;
  
  public HDFSSinkPartitionStreamWriter(SegmentStorage<Record> storage, PartitionStreamConfig descriptor) throws IOException {
    super(storage);
    this.descriptor = descriptor ;
    smallDataSizeAccumulate  = storage.getBufferSegments().dataSize();
    mediumDataSizeAccumulate = storage.getSmallSegments().dataSize();
  }

  public PartitionStreamConfig getDescriptor() { return descriptor; }
  
  @Override
  public void append(Record obj) throws Exception {
    super.append(obj);
    long dataSize = obj.getData().length + obj.getKey().length() ;
    smallDataSizeAccumulate  += dataSize ;
    mediumDataSizeAccumulate += dataSize ;
  }
  
  @Override
  public void completeCommit() throws Exception {
    super.completeCommit();
    if(smallDataSizeAccumulate >= Segment.SMALL_DATASIZE_THRESHOLD) {
      getStorage().optimizeBufferSegments();
      smallDataSizeAccumulate = 0 ;
    }
    if(mediumDataSizeAccumulate >= Segment.MEDIUM_DATASIZE_THRESHOLD) {
      getStorage().optimizeSmallSegments();
      mediumDataSizeAccumulate = 0 ;
    }
  }
}