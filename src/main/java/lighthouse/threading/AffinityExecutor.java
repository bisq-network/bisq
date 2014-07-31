package lighthouse.threading;

import com.google.common.util.concurrent.Uninterruptibles;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import lighthouse.protocol.LHUtils;

import static com.google.common.base.Preconditions.checkState;

/**
 * An extended executor interface that supports thread affinity assertions and short circuiting.
 */
public interface AffinityExecutor extends Executor
{
    /**
     * Returns true if the current thread is equal to the thread this executor is backed by.
     */
    public boolean isOnThread();

    /**
     * Throws an IllegalStateException if the current thread is equal to the thread this executor is backed by.
     */
    public void checkOnThread();

    /**
     * If isOnThread() then runnable is invoked immediately, otherwise the closure is queued onto the backing thread.
     */
    public void executeASAP(LHUtils.UncheckedRunnable runnable);

    public abstract static class BaseAffinityExecutor implements AffinityExecutor
    {
        protected final Thread.UncaughtExceptionHandler exceptionHandler;

        protected BaseAffinityExecutor()
        {
            exceptionHandler = Thread.currentThread().getUncaughtExceptionHandler();
        }

        @Override
        public abstract boolean isOnThread();

        @Override
        public void checkOnThread()
        {
            checkState(isOnThread(), "On wrong thread: %s", Thread.currentThread());
        }

        @Override
        public void executeASAP(LHUtils.UncheckedRunnable runnable)
        {
            final Runnable command = () -> {
                try
                {
                    runnable.run();
                } catch (Throwable throwable)
                {
                    exceptionHandler.uncaughtException(Thread.currentThread(), throwable);
                }
            };
            if (isOnThread())
                command.run();
            else
            {
                execute(command);
            }
        }

        // Must comply with the Executor definition w.r.t. exceptions here.
        @Override
        public abstract void execute(Runnable command);
    }

    public static AffinityExecutor UI_THREAD = new BaseAffinityExecutor()
    {
        @Override
        public boolean isOnThread()
        {
            return Platform.isFxApplicationThread();
        }

        @Override
        public void execute(Runnable command)
        {
            Platform.runLater(command);
        }
    };

    public static AffinityExecutor SAME_THREAD = new BaseAffinityExecutor()
    {
        @Override
        public boolean isOnThread()
        {
            return true;
        }

        @Override
        public void execute(Runnable command)
        {
            command.run();
        }
    };

    public static class ServiceAffinityExecutor extends BaseAffinityExecutor
    {
        protected AtomicReference<Thread> whichThread = new AtomicReference<>(null);
        private final Thread.UncaughtExceptionHandler handler = Thread.currentThread().getUncaughtExceptionHandler();
        public final ScheduledExecutorService service;

        public ServiceAffinityExecutor(String threadName)
        {
            service = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                thread.setName(threadName);
                thread.setUncaughtExceptionHandler(handler);
                whichThread.set(thread);
                return thread;
            });
        }

        @Override
        public boolean isOnThread()
        {
            return Thread.currentThread() == whichThread.get();
        }

        @Override
        public void execute(Runnable command)
        {
            service.execute(command);
        }
    }

    /**
     * An executor useful for unit tests: allows the current thread to block until a command arrives from another
     * thread, which is then executed. Inbound closures/commands stack up until they are cleared by looping.
     */
    public static class Gate extends BaseAffinityExecutor
    {
        private final Thread thisThread = Thread.currentThread();
        private final LinkedBlockingQueue<Runnable> commandQ = new LinkedBlockingQueue<>();

        @Override
        public boolean isOnThread()
        {
            return Thread.currentThread() == thisThread;
        }

        @Override
        public void execute(Runnable command)
        {
            Uninterruptibles.putUninterruptibly(commandQ, command);
        }

        public void waitAndRun()
        {
            final Runnable runnable = Uninterruptibles.takeUninterruptibly(commandQ);
            System.err.println("Gate running " + runnable);
            runnable.run();
        }

        public int getTaskQueueSize()
        {
            return commandQ.size();
        }
    }
}
