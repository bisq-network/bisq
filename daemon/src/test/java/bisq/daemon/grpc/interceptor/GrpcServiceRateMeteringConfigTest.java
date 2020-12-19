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
    private static Optional<ServerInterceptor> versionServiceInterceptor;

    @BeforeClass
    public static void setup() {
        builder.addCallRateMeter(GrpcVersionService.class.getSimpleName(),
                "getVersion",
                2,
                SECONDS);
        builder.addCallRateMeter(GrpcVersionService.class.getSimpleName(),
                "getNadaButDoNotBreakAnything",
                100,
                DAYS);

        // The other Grpc*Service classes are not @VisibleForTesting, so we hardcode
        // the simple class name.
        builder.addCallRateMeter("GrpcOffersService",
                "createOffer",
                5,
                MINUTES);
        builder.addCallRateMeter("GrpcOffersService",
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
        String expectedConfigFilePath = Paths.get(getProperty("java.io.tmpdir")) + File.separator + "ratemeters.json";
        assertEquals(expectedConfigFilePath, configFile.getAbsolutePath());
    }

    @Test
    public void testGrpcVersionServiceRateMeteringConfig() {
        CallRateMeteringInterceptor versionServiceInterceptor = buildInterceptor();
        assertEquals(2, versionServiceInterceptor.serviceCallRateMeters.size());

        GrpcCallRateMeter versionCallRateMeter = versionServiceInterceptor.serviceCallRateMeters.get("getVersion");
        assertFalse(versionCallRateMeter.isRunning());
        assertEquals(2, versionCallRateMeter.getAllowedCallsPerTimeUnit());
        assertEquals(SECONDS, versionCallRateMeter.getTimeUnit());
        assertFalse(versionCallRateMeter.isCallRateExceeded());
        for (int i = 1; i <= 3; i++) {
            versionCallRateMeter.incrementCallsCount();
        }
        assertEquals(3, versionCallRateMeter.getCallsCount());
        assertTrue(versionCallRateMeter.isCallRateExceeded());
    }

    @Test
    public void testRunningRateMetering() {
        CallRateMeteringInterceptor versionServiceInterceptor = buildInterceptor();
        GrpcCallRateMeter versionCallRateMeter = versionServiceInterceptor.serviceCallRateMeters.get("getVersion");

        versionCallRateMeter.start();
        assertTrue(versionCallRateMeter.isRunning());

        // The timer resets the call count to 0 every 1s (the meter's configured timeunit).
        // Wait 1.1s to let it do that before bumping the call count and checking state.
        rest(1100);

        assertEquals(0, versionCallRateMeter.getCallsCount());
        assertFalse(versionCallRateMeter.isCallRateExceeded());

        // Simulate calling 'getVersion' three times.
        for (int i = 1; i <= 3; i++) {
            versionCallRateMeter.incrementCallsCount();
        }

        assertEquals(3, versionCallRateMeter.getCallsCount());
        assertTrue(versionCallRateMeter.isCallRateExceeded());
        versionCallRateMeter.stop();
        assertFalse(versionCallRateMeter.isRunning());
        log.debug("Configured {}", versionServiceInterceptor);
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
        if (versionServiceInterceptor == null) {
            versionServiceInterceptor = getCustomRateMeteringInterceptor(
                    configFile.getParentFile(),
                    GrpcVersionService.class);
        }
        assertTrue(versionServiceInterceptor.isPresent());
        return (CallRateMeteringInterceptor) versionServiceInterceptor.get();
    }
}
