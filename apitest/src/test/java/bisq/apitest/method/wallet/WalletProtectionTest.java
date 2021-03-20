package bisq.apitest.method.wallet;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;



import bisq.apitest.method.MethodTest;

@SuppressWarnings("ResultOfMethodCallIgnored")
@Disabled
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
        aliceClient.setWalletPassword("first-password");
    }

    @Test
    @Order(2)
    public void testGetBalanceOnEncryptedWalletShouldThrowException() {
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.getBtcBalances());
        assertEquals("UNKNOWN: wallet is locked", exception.getMessage());
    }

    @Test
    @Order(3)
    public void testUnlockWalletFor4Seconds() {
        aliceClient.unlockWallet("first-password", 4);
        aliceClient.getBtcBalances(); // should not throw 'wallet locked' exception
        sleep(4500); // let unlock timeout expire
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.getBtcBalances());
        assertEquals("UNKNOWN: wallet is locked", exception.getMessage());
    }

    @Test
    @Order(4)
    public void testGetBalanceAfterUnlockTimeExpiryShouldThrowException() {
        aliceClient.unlockWallet("first-password", 3);
        sleep(4000); // let unlock timeout expire
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.getBtcBalances());
        assertEquals("UNKNOWN: wallet is locked", exception.getMessage());
    }

    @Test
    @Order(5)
    public void testLockWalletBeforeUnlockTimeoutExpiry() {
        aliceClient.unlockWallet("first-password", 60);
        aliceClient.lockWallet();
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.getBtcBalances());
        assertEquals("UNKNOWN: wallet is locked", exception.getMessage());
    }

    @Test
    @Order(6)
    public void testLockWalletWhenWalletAlreadyLockedShouldThrowException() {
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.lockWallet());
        assertEquals("UNKNOWN: wallet is already locked", exception.getMessage());
    }

    @Test
    @Order(7)
    public void testUnlockWalletTimeoutOverride() {
        aliceClient.unlockWallet("first-password", 2);
        sleep(500); // override unlock timeout after 0.5s
        aliceClient.unlockWallet("first-password", 6);
        sleep(5000);
        aliceClient.getBtcBalances(); // getbalance 5s after overriding timeout to 6s
    }

    @Test
    @Order(8)
    public void testSetNewWalletPassword() {
        aliceClient.setWalletPassword("first-password", "second-password");
        sleep(2500); // allow time for wallet save
        aliceClient.unlockWallet("second-password", 2);
        aliceClient.getBtcBalances();
    }

    @Test
    @Order(9)
    public void testSetNewWalletPasswordWithIncorrectNewPasswordShouldThrowException() {
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.setWalletPassword("bad old password", "irrelevant"));
        assertEquals("UNKNOWN: incorrect old password", exception.getMessage());
    }

    @Test
    @Order(10)
    public void testRemoveNewWalletPassword() {
        aliceClient.removeWalletPassword("second-password");
        aliceClient.getBtcBalances();  // should not throw 'wallet locked' exception
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
