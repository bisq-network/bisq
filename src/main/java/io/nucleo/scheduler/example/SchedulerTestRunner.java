package io.nucleo.scheduler.example;

import io.nucleo.scheduler.worker.Worker;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchedulerTestRunner implements WorkerResultHandler, WorkerFaultHandler
{
    private static final Logger log = LoggerFactory.getLogger(SchedulerTestRunner.class);
    private static SchedulerTestRunner schedulerTestRunner;

    public SchedulerTestRunner()
    {
      /*  Map<Object, Object> flashVars = new HashMap<>();
        flashVars.put("userName", "bully");

        Object model = new ExamplePropertyProviderModel(flashVars);
        ExampleAS3Scheduler exampleScheduler = new ExampleAS3Scheduler();

        exampleScheduler.setModel(model);
        exampleScheduler.setResultHandler(() -> {
            log.debug("setResultHandler ");
        });
        exampleScheduler.setFaultHandler((throwable) -> {
            log.debug("setFaultHandler ");
        });
        exampleScheduler.execute();  */
    }

    public static void main(String[] args)
    {
        schedulerTestRunner = new SchedulerTestRunner();
    }

    @Override
    public void onFault(Throwable throwable)
    {
        log.debug("onFault " + this);
    }

    @Override
    public void onResult(Worker worker)
    {
        log.debug("onResult " + this);
    }
}
