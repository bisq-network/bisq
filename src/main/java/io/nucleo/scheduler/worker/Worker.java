package io.nucleo.scheduler.worker;

/**
 * The base interface for all runnable objects (tasks, schedulers)
 */
public interface Worker
{
    /**
     * Starts the execution.
     */
    void execute();

    void addResultHandlers(WorkerResultHandler resultHandler);

    void addFaultHandlers(WorkerFaultHandler faultHandler);

    boolean getHasCompleted();

}
