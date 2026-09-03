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

package bisq.desktop.common;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.reactfx.FxTimer;

import javafx.application.Platform;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UITimer implements Timer {
    private final Logger log = LoggerFactory.getLogger(UITimer.class);
    private bisq.common.reactfx.Timer timer;
    private volatile boolean stopped;

    public UITimer() {
    }

    @Override
    public Timer runLater(Duration delay, Runnable runnable) {
        executeDirectlyIfPossible(() -> {
            if (timer == null) {
                stopped = false;
                timer = FxTimer.create(delay, guarded(runnable));
                timer.restart();
            } else {
                log.warn("runLater called on an already running timer.");
            }
        });
        return this;
    }

    @Override
    public Timer runPeriodically(Duration interval, Runnable runnable) {
        executeDirectlyIfPossible(() -> {
            if (timer == null) {
                stopped = false;
                timer = FxTimer.createPeriodic(interval, guarded(runnable));
                timer.restart();
            } else {
                log.warn("runPeriodically called on an already running timer.");
            }
        });
        return this;
    }

    @Override
    public void stop() {
        stopped = true;
        executeDirectlyIfPossible(() -> {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
        });
    }

    private void executeDirectlyIfPossible(Runnable runnable) {
        // JavaFX disposes its toolkit from a JVM shutdown hook which runs concurrently with
        // Bisq's hook. At that point timelines must not be accessed from the replacement
        // shutdown UserThread; their guarded actions have already become no-ops.
        if (UserThread.isJvmShutdownInProgress()) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            UserThread.execute(runnable);
        }
    }

    private Runnable guarded(Runnable runnable) {
        return () -> {
            if (!stopped && !UserThread.isJvmShutdownInProgress()) {
                runnable.run();
            }
        };
    }
}
