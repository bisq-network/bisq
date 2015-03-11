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

package io.bitsquare.util.tasks;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Task<T extends SharedModel> {
    private static final Logger log = LoggerFactory.getLogger(Task.class);

    private final TaskRunner taskHandler;
    protected final T model;

    public Task(TaskRunner taskHandler, T model) {
        this.taskHandler = taskHandler;
        this.model = model;
    }

    protected void run() {
    }

    protected void complete() {
        taskHandler.complete();
    }

    protected void failed(String message) {
        taskHandler.handleFault(message);
    }

    protected void failed(String message, @NotNull Throwable throwable) {
        log.error(message);
        log.error(throwable.getMessage());
        taskHandler.handleFault(message, throwable);
    }

    protected void failed(@NotNull Throwable throwable) {
        taskHandler.handleFault(throwable.getMessage(), throwable);
    }
}
