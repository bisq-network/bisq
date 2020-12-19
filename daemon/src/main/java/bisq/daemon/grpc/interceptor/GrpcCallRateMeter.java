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

package bisq.daemon.grpc.interceptor;

import bisq.common.Timer;
import bisq.common.UserThread;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static java.lang.String.format;

@Slf4j
public final class GrpcCallRateMeter {

    @Getter
    private final int allowedCallsPerTimeUnit;
    @Getter
    private final TimeUnit timeUnit;
    @Getter
    private int callsCount = 0;
    @Getter
    private transient boolean isRunning;

    @Nullable
    private Timer timer;

    public GrpcCallRateMeter(int allowedCallsPerTimeUnit, TimeUnit timeUnit) {
        this.allowedCallsPerTimeUnit = allowedCallsPerTimeUnit;
        this.timeUnit = timeUnit;
    }

    public void start() {
        stop();
        timer = UserThread.runPeriodically(() -> callsCount = 0, 1, timeUnit);
        isRunning = true;
    }

    public void stop() {
        if (timer != null)
            timer.stop();

        isRunning = false;
    }

    public void incrementCallsCount() {
        callsCount++;
    }

    public boolean isCallRateExceeded() {
        return callsCount > allowedCallsPerTimeUnit;
    }

    public String getCallsCountProgress(String calledMethodName) {
        String shortTimeUnitName = StringUtils.chop(timeUnit.name().toLowerCase());
        return format("%s has been called %d time%s in the last %s, rate limit is %d/%s",
                calledMethodName,
                callsCount,
                callsCount == 1 ? "" : "s",
                shortTimeUnitName,
                allowedCallsPerTimeUnit,
                shortTimeUnitName);
    }

    @Override
    public String toString() {
        return "GrpcCallRateMeter{" +
                "allowedCallsPerTimeUnit=" + allowedCallsPerTimeUnit +
                ", timeUnit=" + timeUnit.name() +
                ", callsCount=" + callsCount +
                ", isRunning=" + isRunning +
                '}';
    }
}
