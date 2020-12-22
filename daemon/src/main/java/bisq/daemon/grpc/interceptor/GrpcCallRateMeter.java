package bisq.daemon.grpc.interceptor;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;

@Slf4j
public class GrpcCallRateMeter {

    @Getter
    private final int allowedCallsPerTimeUnit;
    @Getter
    private final TimeUnit timeUnit;
    @Getter
    private final long timeUnitIntervalInMilliseconds;
    private final ArrayDeque<Long> callTimestamps;

    // The total number of calls made within the current time window.
    private int callsCount;

    public GrpcCallRateMeter(int allowedCallsPerTimeUnit, TimeUnit timeUnit) {
        this.allowedCallsPerTimeUnit = allowedCallsPerTimeUnit;
        this.timeUnit = timeUnit;
        this.timeUnitIntervalInMilliseconds = timeUnit.toMillis(1);
        this.callsCount = 0;
        this.callTimestamps = new ArrayDeque<>();
    }

    public boolean isAllowed() {
        removeStaleCallTimestamps();
        if (callsCount < allowedCallsPerTimeUnit) {
            incrementCallsCount();
            return true;
        } else {
            return false;
        }
    }

    public int getCallsCount() {
        removeStaleCallTimestamps();
        return callsCount;
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

    private void incrementCallsCount() {
        callTimestamps.add(currentTimeMillis());
        callsCount++;
    }

    private void removeStaleCallTimestamps() {
        while (!callTimestamps.isEmpty() && isStale.test(callTimestamps.peek())) {
            callTimestamps.remove();
            callsCount--; // updates the current time window's call count
        }
    }

    private final Predicate<Long> isStale = (t) -> {
        long stale = currentTimeMillis() - this.getTimeUnitIntervalInMilliseconds();
        // Is the given timestamp before the current time minus 1 timeUnit in millis?
        return t < stale;
    };

    @Override
    public String toString() {
        return "GrpcCallRateMeter{" +
                "allowedCallsPerTimeUnit=" + allowedCallsPerTimeUnit +
                ", timeUnit=" + timeUnit.name() +
                ", callsCount=" + callsCount +
                '}';
    }
}
