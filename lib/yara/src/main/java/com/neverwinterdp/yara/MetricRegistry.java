package com.neverwinterdp.yara;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MetricRegistry implements Serializable {
  transient private MetricPluginManager pluginManager = new MetricPluginManager() ;;
  
  private String name ;
  private ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
  private ConcurrentMap<String, Timer>   timers   = new ConcurrentHashMap<>();
  private ConcurrentMap<String, Meter>   meters   = new ConcurrentHashMap<>();

  public MetricRegistry() { 
  }
  
  public MetricRegistry(String name) { 
    this.name = name ;
    if(this.name == null) this.name = "unknown" ;
  }
  
  public String getName() { return this.name ; }
  
  public MetricPluginManager getPluginManager() { return this.pluginManager ; }
  
  public Map<String, Counter> getCounters() { return this.counters ; }
  
  public Counter getCounter(String name) {
    Counter counter = counters.get(name) ;
    if(counter != null) return counter ;
    synchronized(counters) {
      counter = counters.get(name) ;
      if(counter != null) return counter ;
      counter = new Counter(name) ;
      counter.setMetricPlugin(pluginManager);
      counters.put(name, counter) ;
    }
    return counter ;
  }
  
  public Counter counter(String ... name) {
    return getCounter(name(name)) ;
  }
  
  public Map<String, Timer> getTimers() { return this.timers ; }
  
  public Timer getTimer(String name) {
    Timer timer = timers.get(name) ;
    if(timer != null) return timer ;
    synchronized(timers) {
      timer = timers.get(name) ;
      if(timer != null) return timer ;
      timer = new Timer(name) ;
      timer.setMetricPlugin(pluginManager);
      timers.put(name, timer) ;
    }
    return timer ;
  }
  
  public Timer timer(String ... name) {
    return getTimer(name(name)) ;
  }

  public Map<String, Meter> getMeters() { return this.meters ; }
  
  public Meter getMeter(String name, String unit) {
    Meter meter = meters.get(name) ;
    if(meter != null) return meter ;
    synchronized(meters) {
      meter = meters.get(name) ;
      if(meter != null) return meter ;
      meter = new Meter(name, unit) ;
      meter.setMetricPlugin(pluginManager);
      meters.put(name, meter) ;
    }
    return meter ;
  }
  
  public Meter meter(String ... name) {
    return getMeter(name(name), "call") ;
  }
  
  public int remove(String nameExp) {
    return 0 ;
  }
  
  private String name(String ... part) {
    StringBuilder b = new StringBuilder() ;
    for(int i = 0; i < part.length; i++) {
      if(i > 0) b.append(":") ;
      b.append(part[i]) ;
    }
    return b.toString() ;
  }
  
  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    pluginManager = new MetricPluginManager() ;
    in.defaultReadObject();
  }
}
