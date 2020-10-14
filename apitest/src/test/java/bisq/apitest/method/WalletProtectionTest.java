package bisq.apitest.method;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@SuppressWarnings("ResultOfMethodCallIgnored")
@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class WalletProtectionTest extends MethodTest {

    @BeforeAll
    public static void setUp() {
        try {
            setUpScaffold(alicedaemon);
            MILLISECONDS.sleep(2000);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(1)
    public void testSetWalletPassword() {
        var request = createSetWalletPasswordRequest("first-password");
        grpcStubs(alicedaemon).walletsService.setWalletPassword(request);
    }

    @Test
    @Order(2)
    public void testGetBalanceOnEncryptedWalletShouldThrowException() {
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> getBalance(alicedaemon));
        assertEquals("UNKNOWN: wallet is locked", exception.getMessage());
    }

    @Test
    @Order(3)
    public void testUnlockWalletFor4Seconds() {
        var request = createUnlockWalletRequest("first-password", 4);
        grpcStubs(alicedaemon).walletsService.unlockWallet(request);
        getBalance(alicedaemon); // should not throw 'wallet locked' exception
        sleep(4500); // let unlock timeout expire
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> getBalance(alicedaemon));
        assertEquals("UNKNOWN: wallet is locked", exception.getMessage());
    }

    @Test
    @Order(4)
    public void testGetBalanceAfterUnlockTimeExpiryShouldThrowException() {
        var request = createUnlockWalletRequest("first-password", 3);
        grpcStubs(alicedaemon).walletsService.unlockWallet(request);
        sleep(4000); // let unlock timeout expire
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> getBalance(alicedaemon));
        assertEquals("UNKNOWN: wallet is locked", exception.getMessage());
    }

    @Test
    @Order(5)
    public void testLockWalletBeforeUnlockTimeoutExpiry() {
        unlockWallet(alicedaemon, "first-password", 60);
        var request = createLockWalletRequest();
        grpcStubs(alicedaemon).walletsService.lockWallet(request);
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> getBalance(alicedaemon));
        assertEquals("UNKNOWN: wallet is locked", exception.getMessage());
    }

    @Test
    @Order(6)
    public void testLockWalletWhenWalletAlreadyLockedShouldThrowException() {
        var request = createLockWalletRequest();
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                grpcStubs(alicedaemon).walletsService.lockWallet(request));
        assertEquals("UNKNOWN: wallet is already locked", exception.getMessage());
    }

    @Test
    @Order(7)
    public void testUnlockWalletTimeoutOverride() {
        unlockWallet(alicedaemon, "first-password", 2);
        sleep(500); // override unlock timeout after 0.5s
        unlockWallet(alicedaemon, "first-password", 6);
        sleep(5000);
        getBalance(alicedaemon);   // getbalance 5s after resetting unlock timeout to 6s
    }

    @Test
    @Order(8)
    public void testSetNewWalletPassword() {
        var request = createSetWalletPasswordRequest(
                "first-password", "second-password");
        grpcStubs(alicedaemon).walletsService.setWalletPassword(request);
        unlockWallet(alicedaemon, "second-password", 2);
        getBalance(alicedaemon);
        sleep(2500); // allow time for wallet save
    }

    @Test
    @Order(9)
    public void testSetNewWalletPasswordWithIncorrectNewPasswordShouldThrowException() {
        var request = createSetWalletPasswordRequest(
                "bad old password", "irrelevant");
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                grpcStubs(alicedaemon).walletsService.setWalletPassword(request));
        assertEquals("UNKNOWN: incorrect old password", exception.getMessage());
    }

    @Test
    @Order(10)
    public void testRemoveNewWalletPassword() {
        var request = createRemoveWalletPasswordRequest("second-password");
        grpcStubs(alicedaemon).walletsService.removeWalletPassword(request);
        getBalance(alicedaemon);  // should not throw 'wallet locked' exception
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
