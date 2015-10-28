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

package io.bitsquare.common;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class UserThread {

    public static Executor getExecutor() {
        return executor;
    }

    public static void setExecutor(Executor executor) {
        UserThread.executor = executor;
    }

    public static Executor executor = Executors.newSingleThreadExecutor();

    public static void execute(Runnable command) {
        UserThread.executor.execute(command);
    }
}
