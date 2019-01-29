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

/**
 * Gate pattern to help with thread synchronization
 *
 * @author Florian Reimair
 */
public class ThreadGate {

    private CountDownLatch lock = new CountDownLatch(0);

    /**
     * Make everyone wait until the gate is opened again.
     */
    public void engage() {
        lock = new CountDownLatch(1);
    }

    /**
     * Wait for the gate to be opened. Blocks until the gate is opened again.
     * Returns immediately if the gate is already open.
     */
    public synchronized void await() {
        while (lock.getCount() > 0)
            try {
                lock.await();
            } catch (InterruptedException ignore) {
            }
    }

    /**
     * Open the gate and let everyone proceed with their execution.
     */
    public void proceed() {
        lock.countDown();
    }
}
