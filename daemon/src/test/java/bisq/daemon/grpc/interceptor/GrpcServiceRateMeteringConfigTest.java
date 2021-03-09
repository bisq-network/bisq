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

import io.grpc.ServerInterceptor;

import java.nio.file.Paths;

import java.io.File;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static bisq.daemon.grpc.interceptor.GrpcServiceRateMeteringConfig.getCustomRateMeteringInterceptor;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;



import bisq.daemon.grpc.GrpcVersionService;

@Slf4j
public class GrpcServiceRateMeteringConfigTest {

    private static final GrpcServiceRateMeteringConfig.Builder builder = new GrpcServiceRateMeteringConfig.Builder();
    private static File configFile;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Optional<ServerInterceptor> versionServiceInterceptor;

    @BeforeClass
    public static void setup() {
        // This is the tested rate meter, it allows 3 calls every 2 seconds.
        builder.addCallRateMeter(GrpcVersionService.class.getSimpleName(),
                "getVersion",
                3,
                SECONDS,
                2);
        builder.addCallRateMeter(GrpcVersionService.class.getSimpleName(),
                "badMethodNameDoesNotBreakAnything",
                100,
                DAYS);
        // The other Grpc*Service classes are not @VisibleForTesting, so we hardcode
        // the simple class name.
        builder.addCallRateMeter("GrpcOffersService",
                "createOffer",
                5,
                MINUTES);
        builder.addCallRateMeter("GrpcTradesService",
                "takeOffer",
                10,
                DAYS);
        builder.addCallRateMeter("GrpcWalletsService",
                "sendBtc",
                3,
                HOURS);
    }

    @Before
    public void buildConfigFile() {
        if (configFile == null)
            configFile = builder.build();
    }

    @Test
    public void testConfigFileBuild() {
        assertNotNull(configFile);
        assertTrue(configFile.exists());
        assertTrue(configFile.length() > 0);
        String expectedConfigFilePath = Paths.get(getProperty("java.io.tmpdir"), "ratemeters.json").toString();
        assertEquals(expectedConfigFilePath, configFile.getAbsolutePath());
    }

    @Test
    public void testGetVersionCallRateMeter() {
        // Check the interceptor has 2 rate meters, for getVersion and badMethodNameDoesNotBreakAnything.
        CallRateMeteringInterceptor versionServiceInterceptor = buildInterceptor();
        assertEquals(2, versionServiceInterceptor.serviceCallRateMeters.size());

        // Check the rate meter config.
        GrpcCallRateMeter rateMeter = versionServiceInterceptor.serviceCallRateMeters.get("getVersion");
        assertEquals(3, rateMeter.getAllowedCallsPerTimeWindow());
        assertEquals(SECONDS, rateMeter.getTimeUnit());
        assertEquals(2, rateMeter.getNumTimeUnits());
        assertEquals(2 * 1000, rateMeter.getTimeUnitIntervalInMilliseconds());

        // Do as many calls as allowed within rateMeter.getTimeUnitIntervalInMilliseconds().
        doMaxIsAllowedChecks(true,
                rateMeter.getAllowedCallsPerTimeWindow(),
                rateMeter);

        // The next 3 calls are blocked because we've exceeded the 3calls/2s limit.
        doMaxIsAllowedChecks(false,
                rateMeter.getAllowedCallsPerTimeWindow(),
                rateMeter);

        // Let all of the rate meter's cached call timestamps become stale by waiting for
        // 2001 ms, then we can call getversion another 'allowedCallsPerTimeUnit' times.
        rest(1 + rateMeter.getTimeUnitIntervalInMilliseconds());
        // All the stale call timestamps are gone and the call count is back to zero.
        assertEquals(0, rateMeter.getCallsCount());

        doMaxIsAllowedChecks(true,
                rateMeter.getAllowedCallsPerTimeWindow(),
                rateMeter);
        // We've exceeded the call/second limit.
        assertFalse(rateMeter.checkAndIncrement());

        // Let all of the call timestamps go stale again by waiting for 2001 ms.
        rest(1 + rateMeter.getTimeUnitIntervalInMilliseconds());

        // Call twice, resting 0.5s after each call.
        for (int i = 0; i < 2; i++) {
            assertTrue(rateMeter.checkAndIncrement());
            rest(500);
        }
        // Call the 3rd time, then let one of the rate meter's timestamps go stale.
        assertTrue(rateMeter.checkAndIncrement());
        rest(1001);

        // The call count was decremented by one because one timestamp went stale.
        assertEquals(2, rateMeter.getCallsCount());
        assertTrue(rateMeter.checkAndIncrement());
        assertEquals(rateMeter.getAllowedCallsPerTimeWindow(), rateMeter.getCallsCount());

        // We've exceeded the call limit again.
        assertFalse(rateMeter.checkAndIncrement());
    }

    private void doMaxIsAllowedChecks(boolean expectedIsAllowed,
                                      int expectedCallsCount,
                                      GrpcCallRateMeter rateMeter) {
        for (int i = 1; i <= rateMeter.getAllowedCallsPerTimeWindow(); i++) {
            assertEquals(expectedIsAllowed, rateMeter.checkAndIncrement());
        }
        assertEquals(expectedCallsCount, rateMeter.getCallsCount());
    }

    @AfterClass
    public static void teardown() {
        if (configFile != null)
            configFile.deleteOnExit();
    }

    private void rest(long milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException ignored) {
        }
    }

    private CallRateMeteringInterceptor buildInterceptor() {
        //noinspection OptionalAssignedToNull
        if (versionServiceInterceptor == null) {
            versionServiceInterceptor = getCustomRateMeteringInterceptor(
                    configFile.getParentFile(),
                    GrpcVersionService.class);
        }
        assertTrue(versionServiceInterceptor.isPresent());
        return (CallRateMeteringInterceptor) versionServiceInterceptor.get();
    }
}
