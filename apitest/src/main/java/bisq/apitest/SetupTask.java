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

package bisq.apitest;

import java.time.LocalDateTime;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;



import bisq.apitest.linux.LinuxProcess;

@Slf4j
public class SetupTask implements Callable<SetupTask.Status> {

    private final LinuxProcess linuxProcess;
    private final CountDownLatch countdownLatch;

    public SetupTask(LinuxProcess linuxProcess, CountDownLatch countdownLatch) {
        this.linuxProcess = linuxProcess;
        this.countdownLatch = countdownLatch;
    }

    @Override
    public Status call() throws Exception {
        try {
            linuxProcess.start();              // always runs in background
            MILLISECONDS.sleep(1000);  // give 1s for bg process to init
        } catch (InterruptedException ex) {
            throw new IllegalStateException(format("Error starting %s", linuxProcess.getName()), ex);
        }
        Objects.requireNonNull(countdownLatch).countDown();
        return new Status(linuxProcess.getName(), LocalDateTime.now());
    }

    public LinuxProcess getLinuxProcess() {
        return linuxProcess;
    }

    public static class Status {
        private final String name;
        private final LocalDateTime startTime;

        public Status(String name, LocalDateTime startTime) {
            super();
            this.name = name;
            this.startTime = startTime;
        }

        public String getName() {
            return name;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        @Override
        public String toString() {
            return "SetupTask.Status [name=" + name + ", completionTime=" + startTime + "]";
        }
    }
}
