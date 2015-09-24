package com.neverwinterdp.vm.environment.yarn;

import java.io.IOException;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;

public class HDFSUtil {
  static public int getStreamId(Path path) {
    String name = path.getName();
    int dashIdx = name.lastIndexOf('-');
    return Integer.parseInt(name.substring(dashIdx + 1)) ;
  }
  
  
  
  static public void concat(FileSystem fs, Path dest, Path[] src) throws IOException {
    if(fs instanceof LocalFileSystem) {
      FSDataOutputStream output = fs.create(dest) ;
      for(int i = 0; i < src.length; i++) {
        FSDataInputStream is = fs.open(src[i]);
        //BufferedInputStream buffer = new BufferedInputStream(is);
        byte[] data = new byte[4912];
        int available = -1;
        while ((available = is.read(data)) > -1) {
          output.write(data, 0, available);
        }
        is.close();
        //buffer.close();
      }
      output.hflush();
      output.close();
    } else {
      fs.concat(dest, src);
    }
  }
  
  static public void concat(FileSystem fs, Path dest, Path[] src, boolean deleteSrc) throws IOException {
    concat(fs, dest, src);
    if(deleteSrc) {
      for(int i = 0; i < src.length; i++) {
        fs.delete(src[i], true);
      }
    }
  }
  
  static public void dump(FileSystem fs, String dir) throws IOException {
    System.out.println("----------------------------------------------------");
    System.out.println(dir);
    System.out.println("----------------------------------------------------");
    Path path = new Path(dir);
    if(!fs.exists(path)) {
      System.out.println(dir + " does not exist!") ;
      return;
    }
    dump(fs, path, "");
  }
  
  static void dump(FileSystem fs, Path path, String indentation) throws IOException {
    System.out.println(indentation + " " + path.getName());
    if(fs.isFile(path)) return;
    FileStatus[] status = fs.listStatus(path) ;
    String nextIndentation = indentation + "  " ;
    for(int i = 0; i < status.length; i++) {
      dump(fs, status[i].getPath(), nextIndentation);
    }
  }
}