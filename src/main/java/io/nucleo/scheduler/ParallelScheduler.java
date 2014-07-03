package io.nucleo.scheduler;

import io.nucleo.scheduler.worker.Worker;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not tested yet as not used...
 */
public class ParallelScheduler extends AbstractScheduler
{
    private static final Logger log = LoggerFactory.getLogger(ParallelScheduler.class);
    private long numberOfChildrenCompleted;


    @Override
    public void execute()
    {
        if (workerElements.size() > 0)
            workerElements.stream().forEach(this::executeWorker);
        else
            complete();
    }

    @Override
    public void onResult(Worker worker)
    {
        Predicate<Worker> notCompleted = w -> !w.getHasCompleted();
        if (workerElements.stream().filter(notCompleted).count() == 0)
            complete();
    }

}
