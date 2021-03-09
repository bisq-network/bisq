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

package bisq.apitest.method;

import io.grpc.StatusRuntimeException;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.common.file.FileUtil.deleteFileIfExists;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;



import bisq.daemon.grpc.GrpcVersionService;
import bisq.daemon.grpc.interceptor.GrpcServiceRateMeteringConfig;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CallRateMeteringInterceptorTest extends MethodTest {

    private static final GetVersionTest getVersionTest = new GetVersionTest();

    private static File callRateMeteringConfigFile;

    @BeforeAll
    public static void setUp() {
        callRateMeteringConfigFile = buildInterceptorConfigFile();
        startSupportingApps(callRateMeteringConfigFile,
                false,
                false,
                bitcoind, alicedaemon);
    }

    @BeforeEach
    public void sleep200Milliseconds() {
        sleep(200);
    }

    @Test
    @Order(1)
    public void testGetVersionCall1IsAllowed() {
        getVersionTest.testGetVersion();
    }

    @Test
    @Order(2)
    public void testGetVersionCall2ShouldThrowException() {
        Throwable exception = assertThrows(StatusRuntimeException.class, getVersionTest::testGetVersion);
        assertEquals("PERMISSION_DENIED: the maximum allowed number of getversion calls (1/second) has been exceeded",
                exception.getMessage());
    }

    @Test
    @Order(3)
    public void testGetVersionCall3ShouldThrowException() {
        Throwable exception = assertThrows(StatusRuntimeException.class, getVersionTest::testGetVersion);
        assertEquals("PERMISSION_DENIED: the maximum allowed number of getversion calls (1/second) has been exceeded",
                exception.getMessage());
    }

    @Test
    @Order(4)
    public void testGetVersionCall4IsAllowed() {
        sleep(1100); // Let the server's rate meter reset the call count.
        getVersionTest.testGetVersion();
    }

    @AfterAll
    public static void tearDown() {
        try {
            deleteFileIfExists(callRateMeteringConfigFile);
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }
        tearDownScaffold();
    }

    public static File buildInterceptorConfigFile() {
        GrpcServiceRateMeteringConfig.Builder builder = new GrpcServiceRateMeteringConfig.Builder();
        builder.addCallRateMeter(GrpcVersionService.class.getSimpleName(),
                "getVersion",
                1,
                SECONDS);
        builder.addCallRateMeter(GrpcVersionService.class.getSimpleName(),
                "shouldNotBreakAnything",
                1000,
                DAYS);
        // Only GrpcVersionService is @VisibleForTesting, so we hardcode the class names.
        builder.addCallRateMeter("GrpcOffersService",
                "createOffer",
                5,
                MINUTES);
        builder.addCallRateMeter("GrpcTradesService",
                "takeOffer",
                10,
                DAYS);
        builder.addCallRateMeter("GrpcTradesService",
                "withdrawFunds",
                3,
                HOURS);
        return builder.build();
    }
}
