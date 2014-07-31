package lighthouse.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LHUtils
{
    private static final Logger log = LoggerFactory.getLogger(LHUtils.class);

    public static List<Path> listDir(Path dir) throws IOException
    {
        List<Path> contents = new LinkedList<>();
        try (Stream<Path> list = Files.list(dir))
        {
            list.forEach(contents::add);
        }
        return contents;
    }

    //region Generic Java 8 enhancements
    public interface UncheckedRun<T>
    {
        public T run() throws Throwable;
    }

    public interface UncheckedRunnable
    {
        public void run() throws Throwable;
    }

    public static <T> T unchecked(UncheckedRun<T> run)
    {
        try
        {
            return run.run();
        } catch (Throwable throwable)
        {
            throw new RuntimeException(throwable);
        }
    }

    public static void uncheck(UncheckedRunnable run)
    {
        try
        {
            run.run();
        } catch (Throwable throwable)
        {
            throw new RuntimeException(throwable);
        }
    }

    public static void ignoreAndLog(UncheckedRunnable runnable)
    {
        try
        {
            runnable.run();
        } catch (Throwable t)
        {
            log.error("Ignoring error", t);
        }
    }

    public static <T> T ignoredAndLogged(UncheckedRun<T> runnable)
    {
        try
        {
            return runnable.run();
        } catch (Throwable t)
        {
            log.error("Ignoring error", t);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T, E extends Throwable> T checkedGet(Future<T> future) throws E
    {
        try
        {
            return future.get();
        } catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        } catch (ExecutionException e)
        {
            throw (E) e.getCause();
        }
    }

    public static boolean didThrow(UncheckedRun run)
    {
        try
        {
            run.run();
            return false;
        } catch (Throwable throwable)
        {
            return true;
        }
    }

    public static boolean didThrow(UncheckedRunnable run)
    {
        try
        {
            run.run();
            return false;
        } catch (Throwable throwable)
        {
            return true;
        }
    }

    public static <T> T stopwatched(String description, UncheckedRun<T> run)
    {
        long now = System.currentTimeMillis();
        T result = unchecked(run::run);
        log.info("{}: {}ms", description, System.currentTimeMillis() - now);
        return result;
    }

    public static void stopwatch(String description, UncheckedRunnable run)
    {
        long now = System.currentTimeMillis();
        uncheck(run::run);
        log.info("{}: {}ms", description, System.currentTimeMillis() - now);
    }

    //endregion
}