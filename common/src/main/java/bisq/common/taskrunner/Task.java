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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Task<T extends Model> {
    private static final Logger log = LoggerFactory.getLogger(Task.class);

    public static Class<? extends Task> taskToIntercept;

    private final TaskRunner taskHandler;
    protected final T model;
    protected String errorMessage = "An error occurred at task: " + getClass().getSimpleName();
    protected boolean completed;

    public Task(TaskRunner taskHandler, T model) {
        this.taskHandler = taskHandler;
        this.model = model;
    }

    protected abstract void run();

    protected void runInterceptHook() {
        if (getClass() == taskToIntercept)
            throw new InterceptTaskException("Task intercepted for testing purpose. Task = " + getClass().getSimpleName());
    }

    protected void appendToErrorMessage(String message) {
        errorMessage += "\n" + message;
    }

    protected void appendExceptionToErrorMessage(Throwable t) {
        if (t.getMessage() != null)
            errorMessage += "\nException message: " + t.getMessage();
        else
            errorMessage += "\nException: " + t.toString();
    }

    protected void complete() {
        completed = true;
        taskHandler.handleComplete();
    }

    protected void failed(String message) {
        appendToErrorMessage(message);
        failed();
    }

    protected void failed(Throwable t) {
        log.error(errorMessage, t);
        taskHandler.handleErrorMessage(errorMessage);
    }

    protected void failed() {
        log.error(errorMessage);
        taskHandler.handleErrorMessage(errorMessage);
    }

}
