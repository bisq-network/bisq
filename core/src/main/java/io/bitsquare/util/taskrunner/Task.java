/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.util.taskrunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Task<T extends SharedModel> {
    private static final Logger log = LoggerFactory.getLogger(Task.class);

    public static Class<? extends Task> taskToInterceptBeforeRun;
    public static Class<? extends Task> taskToInterceptAfterRun;

    private final TaskRunner taskHandler;
    protected final T model;
    protected String errorMessage = "An error occurred at: " + getClass().getSimpleName();

    public Task(TaskRunner taskHandler, T model) {
        this.taskHandler = taskHandler;
        this.model = model;
    }

    protected void run() {
        try {
            interceptBeforeRun();
            doRun();
        } catch (Throwable t) {
            appendExceptionToErrorMessage(t);
            failed();
        }
    }

    abstract protected void doRun();

    abstract protected void updateStateOnFault();

    private void interceptBeforeRun() {
        if (getClass() == taskToInterceptBeforeRun)
            throw new InterceptTaskException("Task intercepted before run got executed. Task = " + getClass().getSimpleName());
    }

    private void interceptBeforeComplete() {
        if (getClass() == taskToInterceptAfterRun)
            throw new InterceptTaskException("Task intercepted before complete was called. Task = " + getClass().getSimpleName());
    }

    protected void appendToErrorMessage(String message) {
        errorMessage += "\n" + message;
    }

    protected void appendExceptionToErrorMessage(Throwable t) {
        errorMessage += "\nException message: " + t.getMessage();
    }

    protected void complete() {
        try {
            interceptBeforeComplete();
        } catch (Throwable t) {
            appendExceptionToErrorMessage(t);
            failed();
        }
        taskHandler.handleComplete();
    }

    protected void failed(String message) {
        appendToErrorMessage(message);
        failed();
    }

    protected void failed(Throwable t) {
        appendExceptionToErrorMessage(t);
        failed();
    }

    protected void failed() {
        updateStateOnFault();
        taskHandler.handleErrorMessage(errorMessage);
    }

}
