/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common.taskrunner;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskRunnerTest {
    @Test
    void withoutCancelEveryTaskRunsAndTheResultIsReported() {
        TestModel model = new TestModel();
        TaskRunner<TestModel> runner = runner(model);
        runner.addTasks(ListeningTask.class, WaitingTask.class, LastTask.class);
        runner.run();

        model.waitingTask.complete();

        assertEquals(List.of("ListeningTask ran", "WaitingTask ran", "LastTask ran", "result"), model.events);
    }

    @Test
    void aCanceledRunnerRunsNoFurtherTaskAndReportsNoResult() {
        TestModel model = new TestModel();
        TaskRunner<TestModel> runner = runner(model);
        runner.addTasks(WaitingTask.class, LastTask.class);
        runner.run();

        runner.cancel();
        model.waitingTask.complete();

        assertEquals(List.of("WaitingTask ran", "WaitingTask canceled"), model.events);
    }

    @Test
    void aFailureAfterTheCancelIsNotReported() {
        TestModel model = new TestModel();
        TaskRunner<TestModel> runner = runner(model);
        runner.addTasks(WaitingTask.class);
        runner.run();

        runner.cancel();
        model.waitingTask.failed("late failure");

        assertEquals(List.of("WaitingTask ran", "WaitingTask canceled"), model.events);
    }

    // A task that completed in run() but keeps a listener, as the tasks that watch the wallet do
    @Test
    void theCancelReachesATaskThatCompletedAlready() {
        TestModel model = new TestModel();
        TaskRunner<TestModel> runner = runner(model);
        runner.addTasks(ListeningTask.class, WaitingTask.class);
        runner.run();

        runner.cancel();

        assertEquals(List.of("ListeningTask ran", "WaitingTask ran", "ListeningTask canceled", "WaitingTask canceled"),
                model.events);
    }

    private static TaskRunner<TestModel> runner(TestModel model) {
        return new TaskRunner<>(model,
                () -> model.events.add("result"),
                errorMessage -> model.events.add("error"));
    }

    static final class TestModel implements Model {
        private final List<String> events = new ArrayList<>();
        private WaitingTask waitingTask;

        @Override
        public void onComplete() {
        }
    }

    abstract static class RecordingTask extends Task<TestModel> {
        RecordingTask(TaskRunner<TestModel> taskHandler, TestModel model) {
            super(taskHandler, model);
        }

        @Override
        protected void onCanceled() {
            model.events.add(getClass().getSimpleName() + " canceled");
        }
    }

    static final class ListeningTask extends RecordingTask {
        ListeningTask(TaskRunner<TestModel> taskHandler, TestModel model) {
            super(taskHandler, model);
        }

        @Override
        protected void run() {
            model.events.add("ListeningTask ran");
            complete();
        }
    }

    // Completes later, as a task that waits for a message or a broadcast
    static final class WaitingTask extends RecordingTask {
        WaitingTask(TaskRunner<TestModel> taskHandler, TestModel model) {
            super(taskHandler, model);
        }

        @Override
        protected void run() {
            model.events.add("WaitingTask ran");
            model.waitingTask = this;
        }
    }

    static final class LastTask extends RecordingTask {
        LastTask(TaskRunner<TestModel> taskHandler, TestModel model) {
            super(taskHandler, model);
        }

        @Override
        protected void run() {
            model.events.add("LastTask ran");
            complete();
        }
    }
}
