package io.nucleo.scheduler;

import com.sun.istack.internal.NotNull;
import io.nucleo.scheduler.worker.Worker;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class SequenceScheduler extends AbstractScheduler
{
    private Iterator<Worker> workerIterator;

    public SequenceScheduler(List<Worker> workerElements, Object model)
    {
        setWorkers(workerElements);
        setModel(model);
    }

    public SequenceScheduler()
    {
    }

    @Override
    public void setWorkers(@NotNull List<Worker> workerElements)
    {
        workerIterator = new LinkedList<>(workerElements).iterator();
    }

    @Override
    public void execute()
    {
        if (workerIterator != null && workerIterator.hasNext())
            executeWorker(workerIterator.next());
        else
            complete();
    }

    @Override
    public void onResult(Worker worker)
    {
        execute();
    }

}
