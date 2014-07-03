package io.nucleo.scheduler;

import com.sun.istack.internal.NotNull;
import io.nucleo.scheduler.worker.AbstractWorker;
import io.nucleo.scheduler.worker.Worker;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractScheduler extends AbstractWorker implements WorkerResultHandler, WorkerFaultHandler
{
    protected List<Worker> workerElements = new ArrayList<>();

    public void setWorkers(@NotNull List<Worker> workerElements)
    {
        this.workerElements = workerElements;
    }

    protected void executeWorker(Worker worker)
    {
        ((AbstractWorker) worker).setModel(model);
        worker.addResultHandlers(this);
        worker.addFaultHandlers(this);
        worker.execute();
    }

    public void onResult()
    {
    }

    public void onFault(Throwable throwable)
    {
        failed(throwable);
    }

}
