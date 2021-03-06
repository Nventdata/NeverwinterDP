package com.neverwinterdp.tool.message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.neverwinterdp.util.text.TabularFormater;

public class PartitionMessageTracker {
  private String partition;
  private int logCount;
  private List<SequenceMap> map = new ArrayList<>();
  private int idealSequenceMapSize = 100;

  public PartitionMessageTracker(int partition) {
    this.partition = Integer.toString(partition);
  }
  
  public PartitionMessageTracker(String partition) {
    this.partition = partition;
  }

  public int getLogCount() { return this.logCount; }

  public int getDuplicatedCount() {
    //sum of duplicates in all maps
    int dups = 0;
    for (SequenceMap sequenceMap : map) {
      dups += sequenceMap.getDuplicatedCount();
    }
    return dups;
  }

  public int getMinMessageId() {
    // min in topmost map
    return map.get(0).from;
  }

  public int getMaxMessageId() {
    //max in lowest map
    return map.get(map.size() - 1).current;
  }

  public boolean isInSequence() {
    boolean inSequence = true;

    SequenceMap prevSeqMap = null;
    for (SequenceMap seqMap : map) {
      if (prevSeqMap != null) {
        inSequence &= prevSeqMap.getCurrent() + 1 == seqMap.getFrom();
      }
      //return on first false. it will always be false after that
      if (!inSequence)
        return false;
      prevSeqMap = seqMap;
    }
    return inSequence;
  }

  public SequenceMap getSequenceMap(int idx) {
    return map.get(idx);
  }

  //TODO: remove this code
  public List<SequenceMap> getSequenceMap() {
    return map;
  }

  public String getPartition() { return this.partition; }

  public int getPartitionAsInt() { return Integer.parseInt(partition); }
  
  public void setIdealSequenceMapSize(int size) {
    this.idealSequenceMapSize = size;
  }

  synchronized public void log(int trackId) {
    try {
      for (int i = 0; i < map.size(); i++) {
        SequenceMap seqMap = map.get(i);
        if (seqMap.isInRange(trackId)) {
          if (i + 1 < map.size()) {
            SequenceMap nextSeqMap = map.get(i + 1);
            if (nextSeqMap.isInRange(trackId)) {
              nextSeqMap.log(trackId);
              return;
            }
          }
          seqMap.log(trackId);
          return;
        }
        if (seqMap.isSmallerRange(trackId)) {
          seqMap = new SequenceMap(trackId);
          map.add(i, seqMap);
          return;
        }
      }
      SequenceMap seqMap = new SequenceMap(trackId);
      map.add(seqMap);
    } finally {
      logCount++;
      if (map.size() > idealSequenceMapSize) {
        optimize();
      }
    }
  }

  synchronized public void optimize() {
    SequenceMap prevSeqMap = null;
    List<SequenceMap> newMap = new ArrayList<>();
    for (int i = 0; i < map.size(); i++) {
      SequenceMap seqMap = map.get(i);
      if (prevSeqMap != null) {
        if (prevSeqMap.canAppend(seqMap)) {
          prevSeqMap.append(seqMap);
        } else {
          newMap.add(seqMap);
          prevSeqMap = seqMap;
        }
      } else {
        newMap.add(seqMap);
        prevSeqMap = seqMap;
      }
    }
    map = newMap;
  }

  public void dump(Appendable out, String title) throws IOException {
    String[] header = {
        "From", "To", "In Sequence", "Duplication"
    };
    TabularFormater formater = new TabularFormater(header);
    formater.setTitle(title);
    SequenceMap prevSeqMap = null;
    for (int i = 0; i < map.size(); i++) {
      SequenceMap seqMap = map.get(i);
      boolean inSequence = true;
      if (prevSeqMap != null) {
        inSequence = prevSeqMap.getCurrent() + 1 == seqMap.getFrom();
      }
      Object[] cells = {
          seqMap.getFrom(), seqMap.getCurrent(), inSequence, seqMap.getDuplicatedDescription()
      };
      formater.addRow(cells);
      prevSeqMap = seqMap;
    }
    out.append(formater.getFormatText());
  }

  static public class SequenceMap {
    private int from;
    private int current;
    private int duplicatedCount;
    private Set<Integer> duplicated = new HashSet<Integer>();

    public SequenceMap(int num) {
      from = num;
      current = num;
    }

    public int getFrom() {
      return this.from;
    }

    public int getCurrent() {
      return this.current;
    }

    public int getDuplicatedCount() {
      return duplicatedCount;
    }

    public boolean canAppend(SequenceMap other) {
      return current + 1 == other.getFrom();
    }

    public void append(SequenceMap other) {
      if (!canAppend(other)) {
        throw new RuntimeException("Cannot append");
      }
      current = other.current;
      duplicatedCount += other.duplicatedCount;
      duplicated.addAll(other.duplicated);
    }

    public List<Integer> getDuplicatedNumbers() {
      List<Integer> holder = new ArrayList<>();
      holder.addAll(duplicated);
      Collections.sort(holder);
      return holder;
    }

    public String getDuplicatedDescription() {
      StringBuilder b = new StringBuilder();
      b.append(duplicatedCount);
      if (duplicatedCount > 0) {
        b.append("[");
        List<Integer> numbers = getDuplicatedNumbers();
        if (numbers.size() < 10) {
          for (int i = 0; i < numbers.size(); i++) {
            if (i > 0)
              b.append(",");
            b.append(numbers.get(i));
          }
        } else {
          for (int i = 0; i < 5; i++) {
            if (i > 0)
              b.append(",");
            b.append(numbers.get(i));
          }
          b.append("...");
          for (int i = numbers.size() - 5; i < numbers.size(); i++) {
            if (i > numbers.size() - 5)
              b.append(",");
            b.append(numbers.get(i));
          }
        }
        b.append("]");
      }
      return b.toString();
    }

    public boolean isInRange(int num) {
      return num >= from && num <= current + 1;
    }

    public boolean isSmallerRange(int num) {
      return num < from;
    }

    public void log(int num) {
      if (num < from || num > current + 1) {
        throw new RuntimeException("The log number " + num + " is not in the range " + from + " - " + (current + 1));
      }

      if (num == current + 1) {
        current++;
      } else {
        duplicatedCount++;
        duplicated.add(num);
      }
    }
  }
}
