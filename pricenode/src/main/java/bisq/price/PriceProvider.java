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

package bisq.price;

import bisq.common.UserThread;

import org.springframework.context.SmartLifecycle;

import java.time.Duration;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PriceProvider<T> implements SmartLifecycle, Supplier<T> {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private final Timer timer = new Timer(true);

    protected final Duration refreshInterval;

    private T cachedResult;

    public PriceProvider(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
        log.info("will refresh every {}", refreshInterval);
    }

    @Override
    public final T get() {
        return cachedResult;
    }

    @Override
    public final void start() {
        // do the initial refresh asynchronously
        UserThread.runAfter(() -> {
            try {
                refresh();
            } catch (Throwable t) {
                log.warn("initial refresh failed", t);
            }
        }, 1, TimeUnit.MILLISECONDS);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    refresh();
                } catch (Throwable t) {
                    // we only log scheduled calls to refresh that fail to ensure that
                    // the application does *not* halt, assuming the failure is temporary
                    // and on the side of the upstream price provider, eg. BitcoinAverage
                    log.warn("refresh failed", t);
                }
            }
        }, refreshInterval.toMillis(), refreshInterval.toMillis());
    }

    private void refresh() {
        long ts = System.currentTimeMillis();

        cachedResult = doGet();

        log.info("refresh took {} ms.", (System.currentTimeMillis() - ts));

        onRefresh();
    }

    protected abstract T doGet();

    protected void onRefresh() {
    }

    @Override
    public void stop() {
        timer.cancel();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public boolean isRunning() {
        return cachedResult != null;
    }

    @Override
    public int getPhase() {
        return 0;
    }
}
