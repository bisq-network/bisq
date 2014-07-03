package io.nucleo.scheduler;

import io.nucleo.scheduler.tasks.SyncWorker1;
import io.nucleo.scheduler.tasks.SyncWorker2;
import io.nucleo.scheduler.worker.Worker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SequenceSchedulerTest
{
    private final boolean[] hasCompleted = new boolean[1];
    private final boolean[] hasFailed = new boolean[1];
    private final boolean[] worker1HasCompleted = new boolean[1];
    private final boolean[] worker1HasFailed = new boolean[1];
    private final boolean[] worker2HasCompleted = new boolean[1];
    private final boolean[] worker2HasFailed = new boolean[1];
    private SequenceScheduler sequenceScheduler;
    private Map<String, String> model = new HashMap<>();
    private List<Worker> workerList = new ArrayList<>();
    private Throwable worker1Throwable;

    @Before
    public void setUp()
    {
        sequenceScheduler = new SequenceScheduler();
        sequenceScheduler.addResultHandlers((worker) -> {
            hasCompleted[0] = true;
        });
        sequenceScheduler.addFaultHandlers(throwable -> {
            hasFailed[0] = true;
        });
    }

    @After
    public void tearDown() throws Exception
    {
        hasCompleted[0] = false;
        hasFailed[0] = false;
        workerList.clear();
        model.clear();
        worker1Throwable = null;
    }

    @Test
    public void testEmpty()
    {
        sequenceScheduler.execute();
        assertTrue(sequenceScheduler.getHasCompleted());
        assertTrue(hasCompleted[0]);
        assertFalse(hasFailed[0]);
    }

    @Test
    public void testEmpty2()
    {
        sequenceScheduler.setWorkers(workerList);
        sequenceScheduler.execute();
        assertTrue(sequenceScheduler.getHasCompleted());
        assertTrue(hasCompleted[0]);
        assertFalse(hasFailed[0]);
    }


    @Test
    public void testOneWithCompleted()
    {
        Worker worker1 = getWorker1(false);
        workerList.add(worker1);
        sequenceScheduler.setWorkers(workerList);
        sequenceScheduler.execute();

        assertTrue(sequenceScheduler.getHasCompleted());
        assertTrue(hasCompleted[0]);
        assertFalse(hasFailed[0]);

        assertTrue(worker1.getHasCompleted());
        assertTrue(worker1HasCompleted[0]);
        assertFalse(worker1HasFailed[0]);
    }

    @Test
    public void testOneWithFailed()
    {
        Worker worker1 = getWorker1(true);
        workerList.add(worker1);
        sequenceScheduler.setWorkers(workerList);
        sequenceScheduler.execute();

        assertFalse(sequenceScheduler.getHasCompleted());
        assertFalse(hasCompleted[0]);
        assertTrue(hasFailed[0]);

        assertFalse(worker1.getHasCompleted());
        assertFalse(worker1HasCompleted[0]);
        assertTrue(worker1HasFailed[0]);
        assertEquals(SyncWorker1.ERR_MSG, worker1Throwable.getMessage());
    }

    // @Test
    public void testTwoCompleted()
    {
        Worker worker1 = getWorker1(false);
        Worker worker2 = getWorker2(false);
        workerList.add(worker1);
        workerList.add(worker2);
        sequenceScheduler.setWorkers(workerList);
        sequenceScheduler.execute();

        assertTrue(sequenceScheduler.getHasCompleted());
        assertTrue(hasCompleted[0]);
        assertFalse(hasFailed[0]);

        assertTrue(worker1.getHasCompleted());
        assertTrue(worker1HasCompleted[0]);
        assertFalse(worker1HasFailed[0]);

        assertTrue(worker2.getHasCompleted());
        assertTrue(worker2HasCompleted[0]);
        assertFalse(worker2HasFailed[0]);
    }

    @Test
    public void testTwoReverseOrder()
    {
        model.put("worker1State", "");
        model.put("worker2State", "");
        Worker worker1 = getWorker1(false);
        Worker worker2 = getWorker2(false);
        workerList.add(worker2);
        workerList.add(worker1);
        sequenceScheduler.setWorkers(workerList);
        sequenceScheduler.setModel(model);
        sequenceScheduler.execute();

        assertEquals(SyncWorker1.STATE, model.get("worker1State"));
        assertEquals(SyncWorker2.STATE, model.get("worker2State"));

        assertTrue(sequenceScheduler.getHasCompleted());
        assertTrue(hasCompleted[0]);
        assertFalse(hasFailed[0]);

        assertTrue(worker1.getHasCompleted());
        assertTrue(worker1HasCompleted[0]);
        assertFalse(worker1HasFailed[0]);

        assertTrue(worker2.getHasCompleted());
        assertTrue(worker2HasCompleted[0]);
        assertFalse(worker2HasFailed[0]);
    }

    @Test
    public void testTwoFirstFailed()
    {
        Worker worker1 = getWorker1(true);
        Worker worker2 = getWorker2(false);
        workerList.add(worker1);
        workerList.add(worker2);
        sequenceScheduler.setWorkers(workerList);
        sequenceScheduler.execute();

        assertFalse(sequenceScheduler.getHasCompleted());
        assertFalse(hasCompleted[0]);
        assertTrue(hasFailed[0]);

        assertFalse(worker1.getHasCompleted());
        assertFalse(worker1HasCompleted[0]);
        assertTrue(worker1HasFailed[0]);

        assertFalse(worker2.getHasCompleted());
        assertFalse(worker2HasCompleted[0]);
        assertFalse(worker2HasFailed[0]);   // second has not been executed and is not failed!
    }

    @Test
    public void testTwoSecondFailed()
    {
        Worker worker1 = getWorker1(false);
        Worker worker2 = getWorker2(true);
        workerList.add(worker1);
        workerList.add(worker2);
        sequenceScheduler.setWorkers(workerList);
        sequenceScheduler.execute();

        assertFalse(sequenceScheduler.getHasCompleted());
        assertFalse(hasCompleted[0]);
        assertTrue(hasFailed[0]);

        assertTrue(worker1.getHasCompleted());
        assertTrue(worker1HasCompleted[0]);
        assertFalse(worker1HasFailed[0]);

        assertFalse(worker2.getHasCompleted());
        assertFalse(worker2HasCompleted[0]);
        assertTrue(worker2HasFailed[0]);   // second has not been executed and is not failed!
    }

    private Worker getWorker1(boolean letItFail)
    {
        Worker worker1 = new SyncWorker1(letItFail);
        worker1.addResultHandlers((worker) -> {
            worker1HasCompleted[0] = true;
        });
        worker1.addFaultHandlers(throwable -> {
            worker1HasFailed[0] = true;
            worker1Throwable = throwable;
        });
        return worker1;
    }

    private Worker getWorker2(boolean letItFail)
    {
        Worker worker2 = new SyncWorker2(letItFail);
        worker2.addResultHandlers((worker) -> {
            worker2HasCompleted[0] = true;
        });
        worker2.addFaultHandlers(throwable -> {
            worker2HasFailed[0] = true;
        });
        return worker2;
    }
}
