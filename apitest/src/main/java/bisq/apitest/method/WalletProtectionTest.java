package bisq.apitest.method;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;



import bisq.apitest.OrderedRunner;
import bisq.apitest.annotation.Order;

@Slf4j
@RunWith(OrderedRunner.class)
public class WalletProtectionTest extends MethodTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @BeforeClass
    public static void setUp() {
        setUpScaffold(alicedaemon.name());
    }

    @Test
    @Order(1)
    public void testSetWalletPassword() {
        var setPasswordRequest = createSetWalletPasswordRequest("password");
        grpcStubs.walletsService.setWalletPassword(setPasswordRequest);
    }

    @Test
    @Order(2)
    public void testGetBalanceOnEncryptedWalletShouldThrowException() {
        exceptionRule.expect(StatusRuntimeException.class);
        exceptionRule.expectMessage("UNKNOWN: wallet is locked");
        getBalance();
    }

    @Test
    @Order(3)
    public void testRemoveWalletPassword() {
        var removePasswordRequest = createRemoveWalletPasswordRequest("password");
        grpcStubs.walletsService.removeWalletPassword(removePasswordRequest);
        getBalance(); // should not throw exception
    }

    @AfterClass
    public static void tearDown() {
        tearDownScaffold();
    }
}
