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

package bisq.common;

import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Runs all listener objects periodically in a short interval.
public class MasterTimer {
    private final static Logger log = LoggerFactory.getLogger(MasterTimer.class);
    private static final java.util.Timer timer = new java.util.Timer();
    // frame rate of 60 fps is about 16 ms but we  don't need such a short interval, 100 ms should be good enough
    public static final long FRAME_INTERVAL_MS = 100;

    static {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                UserThread.execute(() -> listeners.forEach(Runnable::run));
            }
        }, FRAME_INTERVAL_MS, FRAME_INTERVAL_MS);
    }

    private static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();

    public static void addListener(Runnable runnable) {
        listeners.add(runnable);
    }

    public static void removeListener(Runnable runnable) {
        listeners.remove(runnable);
    }
}
