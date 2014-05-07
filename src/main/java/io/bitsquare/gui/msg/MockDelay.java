package io.bitsquare.gui.msg;


import java.util.concurrent.*;

public class MockDelay
{
    public String waitForMsg(String expectedMsg)
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(new Task(expectedMsg));
        // max timeout  5 sec
        try
        {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e)
        {
            e.printStackTrace();
        }
        executor.shutdownNow();
        return null;
    }
}

class Task implements Callable<String>
{
    private String expectedMsg;

    Task(String expectedMsg)
    {
        this.expectedMsg = expectedMsg;
    }

    @Override
    public String call() throws Exception
    {
        Thread.sleep(1000); // 1 seconds pause
        return expectedMsg;
    }
}