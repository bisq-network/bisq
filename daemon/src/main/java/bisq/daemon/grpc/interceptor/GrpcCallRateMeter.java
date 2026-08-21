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
    private final int allowedCallsPerTimeWindow;
    @Getter
    private final TimeUnit timeUnit;
    @Getter
    private final int numTimeUnits;

    @Getter
    private transient final long timeUnitIntervalInMilliseconds;

    private transient final ArrayDeque<Long> callTimestamps;

    public GrpcCallRateMeter(int allowedCallsPerTimeWindow, TimeUnit timeUnit) {
        this(allowedCallsPerTimeWindow, timeUnit, 1);
    }

    public GrpcCallRateMeter(int allowedCallsPerTimeWindow, TimeUnit timeUnit, int numTimeUnits) {
        this.allowedCallsPerTimeWindow = allowedCallsPerTimeWindow;
        this.timeUnit = timeUnit;
        this.numTimeUnits = numTimeUnits;
        this.timeUnitIntervalInMilliseconds = timeUnit.toMillis(1) * numTimeUnits;
        this.callTimestamps = new ArrayDeque<>();
    }

    public boolean checkAndIncrement() {
        if (getCallsCount() < allowedCallsPerTimeWindow) {
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
        // Just print 'GetVersion has been called N times...',
        // not 'io.bisq.protobuffer.GetVersion/GetVersion has been called N times...'
        String loggedMethodName = calledMethodName.split("/")[1];
        return format("%s has been called %d time%s in the last %s, rate limit is %d/%s",
                loggedMethodName,
                callTimestamps.size(),
                callTimestamps.size() == 1 ? "" : "s",
                shortTimeUnitName,
                allowedCallsPerTimeWindow,
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
                "allowedCallsPerTimeWindow=" + allowedCallsPerTimeWindow +
                ", timeUnit=" + timeUnit.name() +
                ", timeUnitIntervalInMilliseconds=" + timeUnitIntervalInMilliseconds +
                ", callsCount=" + callTimestamps.size() +
                '}';
    }
}
