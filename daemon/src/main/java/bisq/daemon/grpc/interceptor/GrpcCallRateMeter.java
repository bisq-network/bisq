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

    public GrpcCallRateMeter(int allowedCallsPerTimeUnit, TimeUnit timeUnit) {
        this.allowedCallsPerTimeUnit = allowedCallsPerTimeUnit;
        this.timeUnit = timeUnit;
        this.timeUnitIntervalInMilliseconds = timeUnit.toMillis(1);
        this.callTimestamps = new ArrayDeque<>();
    }

    public boolean isAllowed() {
        removeStaleCallTimestamps();
        if (callTimestamps.size() < allowedCallsPerTimeUnit) {
            incrementCallsCount();
            return true;
        } else {
            return false;
        }
    }

    public int getCallsCount() {
        removeStaleCallTimestamps();
        return callTimestamps.size();
    }

    public String getCallsCountProgress(String calledMethodName) {
        String shortTimeUnitName = StringUtils.chop(timeUnit.name().toLowerCase());
        return format("%s has been called %d time%s in the last %s, rate limit is %d/%s",
                calledMethodName,
                callTimestamps.size(),
                callTimestamps.size() == 1 ? "" : "s",
                shortTimeUnitName,
                allowedCallsPerTimeUnit,
                shortTimeUnitName);
    }

    private void incrementCallsCount() {
        callTimestamps.add(currentTimeMillis());
    }

    private void removeStaleCallTimestamps() {
        while (!callTimestamps.isEmpty() && isStale.test(callTimestamps.peek())) {
            callTimestamps.remove();
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
                ", callsCount=" + callTimestamps.size() +
                '}';
    }
}
