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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Helps configure listener objects that are run by the `UserThread` each second
// and can do per second, per minute and delayed second actions.
public class Clock {
    private static final Logger log = LoggerFactory.getLogger(Clock.class);

    public static final int IDLE_TOLERANCE = 20000;

    public interface Listener {
        void onSecondTick();

        void onMinuteTick();

        void onMissedSecondTick(long missed);
    }

    private Timer timer;
    private final List<Listener> listeners = new LinkedList<>();
    private long counter = 0;
    private long lastSecondTick;

    public Clock() {
    }

    public void start() {
        if (timer == null) {
            lastSecondTick = System.currentTimeMillis();
            timer = UserThread.runPeriodically(() -> {
                listeners.stream().forEach(Listener::onSecondTick);
                counter++;
                if (counter >= 60) {
                    counter = 0;
                    listeners.stream().forEach(Listener::onMinuteTick);
                }

                long currentTimeMillis = System.currentTimeMillis();
                long diff = currentTimeMillis - lastSecondTick;
                if (diff > 1000)
                    listeners.stream().forEach(listener -> listener.onMissedSecondTick(diff - 1000));

                lastSecondTick = currentTimeMillis;
            }, 1, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        timer.stop();
        timer = null;
        counter = 0;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
