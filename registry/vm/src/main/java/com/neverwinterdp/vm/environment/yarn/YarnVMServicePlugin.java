package com.neverwinterdp.vm.environment.yarn;

import java.io.IOException;

import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mycila.jmx.annotation.JmxBean;
import com.neverwinterdp.registry.RegistryException;
import com.neverwinterdp.vm.VMConfig;
import com.neverwinterdp.vm.VMDescriptor;
import com.neverwinterdp.vm.service.VMService;
import com.neverwinterdp.vm.service.VMServicePlugin;

@Singleton
@JmxBean("role=vm-manager, type=VMServicePlugin, name=YarnVMServicePlugin")
public class YarnVMServicePlugin implements VMServicePlugin {
  private Logger logger = LoggerFactory.getLogger(YarnVMServicePlugin.class);
  
  @Inject
  private YarnManager yarnManager;
  
  @Override
  synchronized public void allocateVM(VMService vmService, final VMConfig vmConfig) throws RegistryException, Exception {
    logger.info("Start allocate request for " + vmConfig.getVmId());
    if(vmConfig.getLocalAppHome() == null) {
      vmConfig.setLocalAppHome("/opt/hadoop/vm/" + vmConfig.getVmId());
    }
    final VMRequest containerReq = 
      yarnManager.createContainerRequest(0, vmConfig.getRequestCpuCores(), vmConfig.getRequestMemory());
    
    YarnManager.ContainerRequestCallback callback = new YarnManager.ContainerRequestCallback() {
      @Override
      public void onAllocate(YarnManager manager, VMRequest containerRequest, Container container) {
        logger.info("Start onAllocate for " + vmConfig.getVmId());
        vmConfig.
          setSelfRegistration(false).
          addHadoopProperty(manager.getYarnConfig());
        try {
          yarnManager.startContainer(container, vmConfig);
        } catch (YarnException | IOException e) {
          logger.error("Cannot start the container", e);
        }
        logger.info("Finish onAllocate  for " + vmConfig.getVmId());
      }
      
      public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Command: " + vmConfig.buildCommand());
        return b.toString();
      }
    };
    yarnManager.allocate(containerReq, callback);
    logger.info("Finish allocate request for " + vmConfig.getVmId());
  }

  @Override
  synchronized public void killVM(VMService vmService, VMDescriptor vmDescriptor) throws Exception {
    logger.info("Start onKill(VMService vmService, VMDescriptor vmDescriptor)");
    logger.info("Finish onKill(VMService vmService, VMDescriptor vmDescriptor)");
  }
  
  @Override
  synchronized public void shutdownVM(VMService vmService, VMDescriptor vmDescriptor) throws Exception {
    logger.info("Start onShutdown(VMService vmService, VMDescriptor vmDescriptor)");
    logger.info("Finish onShutdown(VMService vmService, VMDescriptor vmDescriptor)");
  }

  @Override
  public void shutdown() {
    yarnManager.onDestroy();
  }
}