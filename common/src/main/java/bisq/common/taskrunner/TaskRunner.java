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

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskRunner<T extends Model> {
    private final Queue<Class<? extends Task<T>>> tasks = new LinkedBlockingQueue<>();
    private final T sharedModel;
    private final Class<T> sharedModelClass;
    private final ResultHandler resultHandler;
    private final ErrorMessageHandler errorMessageHandler;
    private boolean failed = false;
    private boolean isCanceled;

    private Class<? extends Task<T>> currentTask;
    // The tasks run so far, so that a cancel also reaches a task that completed but keeps a timer or a listener
    private final List<Task<T>> startedTasks = new ArrayList<>();


    @SuppressWarnings("unchecked")
    public TaskRunner(T sharedModel, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        this(sharedModel, (Class<T>) sharedModel.getClass(), resultHandler, errorMessageHandler);
    }

    public TaskRunner(T sharedModel, Class<T> sharedModelClass, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        this.sharedModel = sharedModel;
        this.resultHandler = resultHandler;
        this.errorMessageHandler = errorMessageHandler;
        this.sharedModelClass = sharedModelClass;
    }

    @SafeVarargs
    public final void addTasks(Class<? extends Task<T>>... items) {
        tasks.addAll(Arrays.asList(items));
    }

    public void run() {
        next();
    }

    private void next() {
        if (!failed && !isCanceled) {
            if (tasks.size() > 0) {
                try {
                    currentTask = tasks.poll();
                    log.info("Run task: " + currentTask.getSimpleName());
                    Task<T> task = currentTask.getDeclaredConstructor(TaskRunner.class, sharedModelClass)
                            .newInstance(this, sharedModel);
                    startedTasks.add(task);
                    task.run();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                    handleErrorMessage("Error at taskRunner: " + throwable.getMessage());
                }
            } else {
                resultHandler.handleResult();
            }
        }
    }

    // No further task runs and neither the result nor an error is reported. Each task that has run is canceled.
    public void cancel() {
        isCanceled = true;
        startedTasks.forEach(Task::cancel);
    }

    void handleComplete() {
        next();
    }

    void handleErrorMessage(String errorMessage) {
        if (isCanceled) {
            log.info("A task failed after the task runner was canceled: {}", errorMessage);
            return;
        }
        log.error("Task failed: " + currentTask.getSimpleName() + " / errorMessage: " + errorMessage);
        failed = true;
        errorMessageHandler.handleErrorMessage(errorMessage);
    }
}
