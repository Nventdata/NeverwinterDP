package com.neverwinterdp.registry.activity;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PreDestroy;

import com.google.inject.Injector;
import com.neverwinterdp.registry.BatchOperations;
import com.neverwinterdp.registry.Node;
import com.neverwinterdp.registry.NodeCreateMode;
import com.neverwinterdp.registry.Registry;
import com.neverwinterdp.registry.RegistryException;
import com.neverwinterdp.registry.SequenceIdTracker;
import com.neverwinterdp.registry.Transaction;
import com.neverwinterdp.registry.queue.DistributedQueue;

public class ActivityService extends ActivityRegistry {
  static DecimalFormat ORDER_FORMATER = new DecimalFormat("#000");

  private Injector          container;
  private SequenceIdTracker activityIdTracker;

  private DistributedQueue            queue;
  private Map<String, ActivityRunner> activeActivities = new ConcurrentHashMap<>();
  private ActivityScheduler           activityScheduler;
  private final Lock                  lock             = new ReentrantLock(true);

  public ActivityService() {
  }

  public ActivityService(Injector container, String activityPath) throws Exception {
    init(container, activityPath);
  }

  protected void init(Injector container, String activityPath) throws Exception {
    this.container = container;
    this.registry  = container.getInstance(Registry.class);
    super.init(registry, activityPath, true);
    queue = new DistributedQueue(registry, activityPath + "/queue");
    activityIdTracker = new SequenceIdTracker(registry, activityPath + "/activity-id-tracker", true);

    for(Activity selActive : getActiveActivities()) {
      resume(selActive);
    }
    
    activityScheduler = new ActivityScheduler();
    activityScheduler.start();
  }

  @PreDestroy
  public void onDestroy() throws InterruptedException {
    queue.shutdown();
    activityScheduler.interrupt();
    int count = 0 ;
    while(activeActivities.size() > 0 && count < 60) {
      Thread.sleep(500);
      count++;
    }
  }

  public Lock getLock() { return this.lock; }

  public <T extends ActivityCoordinator> T getActivityCoordinator(String type) throws Exception {
    return container.getInstance((Class<T>) Class.forName(type));
  }

  public <T extends ActivityCoordinator> T getActivityCoordinator(Class<T> type) throws Exception {
    return container.getInstance(type);
  }

  public <T extends ActivityStepExecutor> T getActivityStepExecutor(String type) throws Exception {
    return container.getInstance((Class<T>) Class.forName(type));
  }

  public void queue(Activity activity) throws Exception {
    queue.offerAs(activity);
  }

  public ActivityExecutionContext run(Activity activity) throws Exception {
    create(activity);
    ActivityExecutionContext context = 
        new ActivityExecutionContext(activity, ActivityService.this);
    ActivityRunner runner = new ActivityRunner(context, activity, false);
    runner.start();
    return context;
  }

  public ActivityExecutionContext resume(Activity activity) throws Exception {
    System.err.println(getClass() + ": resume activity " + activity.getId());
    ActivityExecutionContext context = new ActivityExecutionContext(activity, ActivityService.this);
    ActivityRunner runner = new ActivityRunner(context, activity, true);
    runner.start();
    context.waitForTermination(5000);
    return context;
  }

  
  synchronized public Activity create(Activity activity) throws Exception {
    Class<ActivityStepBuilder> stepBuilderType = 
      (Class<ActivityStepBuilder>) Class.forName(activity.getActivityStepBuilder());
    ActivityStepBuilder stepBuilder = container.getInstance(stepBuilderType);
    List<ActivityStep> activitySteps = stepBuilder.build(activity, container);

    String activityId = activityIdTracker.nextSeqId() + "-" + activity.getType();
    activity.setId(activityId);

    Transaction transaction = registry.getTransaction();
    transaction.createChild(allNode, activityId, activity, NodeCreateMode.PERSISTENT);
    transaction.createDescendant(allNode, activityId + "/activity-steps", NodeCreateMode.PERSISTENT);
    for (int i = 0; i < activitySteps.size(); i++) {
      ActivityStep step = activitySteps.get(i);
      String id = ORDER_FORMATER.format(i) + "-" + step.getType();
      step.setId(id);
      transaction.createDescendant(allNode, activityId + "/activity-steps/" + id, step, NodeCreateMode.PERSISTENT);
    }
    transaction.createChild(activeNode, activityId, NodeCreateMode.PERSISTENT);
    transaction.commit();
    return activity;
  }

  public <T> void updateActivityStepAssigned(final Activity activity, final ActivityStep step)
      throws RegistryException {
    BatchOperations<Boolean> ops = new BatchOperations<Boolean>() {
      @Override
      public Boolean execute(Registry registry) throws RegistryException {
        Node activityStepNode = getActivityStepNode(activity, step);
        Transaction transaction = registry.getTransaction();
        step.setStatus(ActivityStep.Status.ASSIGNED);
        transaction.setData(activityStepNode, step);
        transaction.commit();
        return true;
      }
    };
    registry.executeBatch(ops, 3, 1000);
  }

  public <T> void updateActivityStepExecuting(final Activity activity, final ActivityStep step, final T workerInfo)
      throws RegistryException {
    BatchOperations<Boolean> ops = new BatchOperations<Boolean>() {
      @Override
      public Boolean execute(Registry registry) throws RegistryException {
        Node activityStepNode = getActivityStepNode(activity, step);
        Transaction transaction = registry.getTransaction();
        step.setStatus(ActivityStep.Status.EXECUTING);
        transaction.setData(activityStepNode, step);
        transaction.createChild(activityStepNode, "heartbeat", workerInfo, NodeCreateMode.EPHEMERAL);
        transaction.commit();
        return true;
      }
    };
    registry.executeBatch(ops, 3, 1000);
  }

  public <T> void updateActivityStepFinished(final Activity activity, final ActivityStep step)
      throws RegistryException {
    updateActivityStepFinished(activity, step, ActivityStep.Status.FINISHED);
  }

  public <T> void updateActivityStepFailed(final Activity activity, final ActivityStep step) throws RegistryException {
    updateActivityStepFinished(activity, step, ActivityStep.Status.FINISHED_WITH_ERROR);
  }

  private <T> void updateActivityStepFinished(final Activity activity, final ActivityStep step,
      final ActivityStep.Status status) throws RegistryException {
    BatchOperations<Boolean> ops = new BatchOperations<Boolean>() {
      @Override
      public Boolean execute(Registry registry) throws RegistryException {
        Node activityStepNode = getActivityStepNode(activity, step);
        Transaction transaction = registry.getTransaction();
        step.setStatus(status);
        transaction.setData(activityStepNode, step);
        transaction.deleteChild(activityStepNode, "heartbeat");
        transaction.commit();
        return true;
      }
    };
    registry.executeBatch(ops, 3, 1000);
  }

  public class ActivityScheduler extends Thread {
    public ActivityScheduler() {
      setName("ActivityScheduler");
    }

    public void run() {
      try {
        doRun();
      } catch (InterruptedException e) {
      } catch (DistributedQueue.ShutdownException e) {
      } catch (RegistryException e) {
        if(e.getCause() instanceof InterruptedException) return;
        e.printStackTrace();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    void doRun() throws Exception {
      Activity activity = null;
      while ((activity = queue.takeAs(Activity.class)) != null) {
        activity = create(activity);
        ActivityExecutionContext context = new ActivityExecutionContext(activity, ActivityService.this);
        ActivityRunner runner = new ActivityRunner(context, activity, false);
        runner.start();
      }
    }
  }

  public void kill() throws Exception {
    activityScheduler.interrupt();
    for (ActivityRunner selRunner : activeActivities.values()) {
      selRunner.interrupt();
    }
  }

  public class ActivityRunner extends Thread {
    private Activity                 activity;
    private ActivityExecutionContext context;
    private boolean resume = false;

    public ActivityRunner(ActivityExecutionContext context, Activity activity, boolean resume) {
      setName("activity-runner-for-" + activity.getId());
      this.context = context;
      this.activity = activity;
      this.resume = resume ;
    }

    public void run() {
      activeActivities.put(activity.getId(), this);
      boolean lockAcquired = false;
      try {
        lockAcquired = getLock().tryLock(30, TimeUnit.MINUTES);
        if (!lockAcquired) {
          throw new Exception("Cannet obtain the lock after 15 minutes");
        }
        doRun();
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        activeActivities.remove(activity.getId());
        if (lockAcquired) {
          getLock().unlock();
        }
        context.notifyTermination();
      }
    }

    void doRun() throws Exception {
      ActivityCoordinator coordinator = getActivityCoordinator(activity.getCoordinator());
      context.setActivityCoordinator(coordinator);
      if(resume) coordinator.onResume(context, activity);
      else       coordinator.onStart(context, activity);
    }
  }
}
