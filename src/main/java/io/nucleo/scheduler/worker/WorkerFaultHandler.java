package io.nucleo.scheduler.worker;

public interface WorkerFaultHandler
{
    void onFault(Throwable throwable);
}
