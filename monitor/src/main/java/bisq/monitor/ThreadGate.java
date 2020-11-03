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

package bisq.monitor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Gate pattern to help with thread synchronization
 *
 * @author Florian Reimair
 */
@Slf4j
public class ThreadGate {

    private CountDownLatch lock = new CountDownLatch(0);

    /**
     * Make everyone wait until the gate is open again.
     */
    public void engage() {
        lock = new CountDownLatch(1);
    }

    /**
     * Make everyone wait until the gate is open again.
     *
     * @param numberOfLocks how often the gate has to be unlocked until the gate
     *                      opens.
     */
    public void engage(int numberOfLocks) {
        lock = new CountDownLatch(numberOfLocks);
    }

    /**
     * Wait for the gate to be opened. Blocks until the gate is open again. Returns
     * immediately if the gate is already open.
     */
    public synchronized void await() {
        while (lock.getCount() > 0)
            try {
                if (!lock.await(60, TimeUnit.SECONDS)) {
                    log.warn("timeout occurred!");
                    break; // break the loop
                }
            } catch (InterruptedException ignore) {
            }
    }

    /**
     * Open the gate and let everyone proceed with their execution.
     */
    public void proceed() {
        lock.countDown();
    }

    /**
     * Open the gate with no regards on how many locks are still in place.
     */
    public void unlock() {
        while (lock.getCount() > 0)
            lock.countDown();
    }
}
