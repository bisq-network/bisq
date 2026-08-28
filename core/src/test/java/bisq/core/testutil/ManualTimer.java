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

package bisq.core.testutil;

import bisq.common.Timer;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic replacement for the UserThread timer in tests: install with
 * {@code UserThread.setTimerClass(ManualTimer.class)}, fire the collected timers explicitly
 * and restore {@code FrameRateTimer} afterwards. The collected timers are static state, so
 * call {@code clear()} around each test for isolation.
 */
public class ManualTimer implements Timer {
    private static final List<ManualTimer> timers = new ArrayList<>();

    private Runnable action;
    private boolean stopped;

    public ManualTimer() {
        timers.add(this);
    }

    @Override
    public Timer runLater(Duration delay, Runnable action) {
        this.action = action;
        return this;
    }

    @Override
    public Timer runPeriodically(Duration interval, Runnable action) {
        this.action = action;
        return this;
    }

    @Override
    public void stop() {
        stopped = true;
    }

    public void fire() {
        if (!stopped) {
            action.run();
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    public static ManualTimer latest() {
        return timers.get(timers.size() - 1);
    }

    public static void firePendingTimers() {
        List.copyOf(timers).forEach(ManualTimer::fire);
    }

    public static void clear() {
        timers.clear();
    }
}
