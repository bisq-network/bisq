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

import java.time.Duration;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * We simulate a global frame rate timer similar to FXTimer to avoid creation of threads for each timer call.
 * Used only in headless apps like the seed node.
 */
public class FrameRateTimer implements Timer, Runnable {
    private final Logger log = LoggerFactory.getLogger(FrameRateTimer.class);

    private long interval;
    private Runnable runnable;
    private long startTs;
    private boolean isPeriodically;
    private final String uid = UUID.randomUUID().toString();
    private volatile boolean stopped;

    public FrameRateTimer() {
    }

    @Override
    public void run() {
        if (!stopped) {
            try {
                long currentTimeMillis = System.currentTimeMillis();
                if ((currentTimeMillis - startTs) >= interval) {
                    runnable.run();
                    if (isPeriodically)
                        startTs = currentTimeMillis;
                    else
                        stop();
                }
            } catch (Throwable t) {
                log.error("exception in FrameRateTimer", t);
                stop();
                throw t;
            }
        }
    }

    @Override
    public Timer runLater(Duration delay, Runnable runnable) {
        this.interval = delay.toMillis();
        this.runnable = runnable;
        startTs = System.currentTimeMillis();
        MasterTimer.addListener(this);
        return this;
    }

    @Override
    public Timer runPeriodically(Duration interval, Runnable runnable) {
        this.interval = interval.toMillis();
        isPeriodically = true;
        this.runnable = runnable;
        startTs = System.currentTimeMillis();
        MasterTimer.addListener(this);
        return this;
    }

    @Override
    public void stop() {
        stopped = true;
        MasterTimer.removeListener(this);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameRateTimer)) return false;

        FrameRateTimer that = (FrameRateTimer) o;

        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        return uid != null ? uid.hashCode() : 0;
    }
}
