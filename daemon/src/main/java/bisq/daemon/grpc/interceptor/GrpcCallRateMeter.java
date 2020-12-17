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
    private final long allowedCallsPerTimeUnit;
    @Getter
    private final TimeUnit timeUnit;

    @Getter
    private long callsCount = 0;

    @Getter
    private boolean isRunning;

    @Nullable
    private Timer timer;

    public GrpcCallRateMeter(long allowedCallsPerTimeUnit, TimeUnit timeUnit) {
        this.allowedCallsPerTimeUnit = allowedCallsPerTimeUnit;
        this.timeUnit = timeUnit;
    }

    public void start() {
        if (timer != null)
            timer.stop();

        timer = UserThread.runPeriodically(() -> callsCount = 0, 1, timeUnit);
        isRunning = true;
    }

    public void incrementCallsCount() {
        callsCount++;
    }

    public boolean isCallRateExceeded() {
        return callsCount > allowedCallsPerTimeUnit;
    }

    public String getCallsCountProgress(String calledMethodName) {
        String shortTimeUnitName = StringUtils.chop(timeUnit.name().toLowerCase());
        return format("%s has been called %d time%s in the last %s;  the rate limit is %d/%s.",
                calledMethodName,
                callsCount,
                callsCount == 1 ? "" : "s",
                shortTimeUnitName,
                allowedCallsPerTimeUnit,
                shortTimeUnitName);
    }
}
