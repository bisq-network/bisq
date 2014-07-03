package io.nucleo.scheduler.worker;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractWorker implements Worker
{
    protected List<WorkerResultHandler> resultHandlers = new ArrayList<>();
    protected List<WorkerFaultHandler> faultHandlers = new ArrayList<>();
    protected Object model;
    protected boolean hasFailed;
    protected boolean hasCompleted;

    @Override
    abstract public void execute();

    @Override
    public void addResultHandlers(WorkerResultHandler resultHandler)
    {
        resultHandlers.add(resultHandler);
    }

    @Override
    public void addFaultHandlers(WorkerFaultHandler faultHandler)
    {
        faultHandlers.add(faultHandler);
    }

    public void setModel(Object model)
    {
        this.model = model;
    }

    @Override
    public boolean getHasCompleted()
    {
        return hasCompleted;
    }

    protected void complete()
    {
        hasCompleted = true;
        resultHandlers.stream().forEach(e -> e.onResult(this));
    }

    protected void failed(Throwable throwable)
    {
        hasFailed = true;
        faultHandlers.stream().forEach(e -> e.onFault(throwable));
    }

    protected void destroy()
    {
    }
}
