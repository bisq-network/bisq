package bisq.apitest.scenario;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static org.junit.jupiter.api.Assertions.fail;



import bisq.apitest.method.MethodTest;
import bisq.apitest.method.payment.CreatePaymentAccountTest;
import bisq.apitest.method.payment.GetPaymentMethodsTest;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PaymentAccountTest extends MethodTest {

    @BeforeAll
    public static void setUp() {
        try {
            setUpScaffold(bitcoind, seednode, arbdaemon, alicedaemon);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(1)
    public void testGetPaymentMethods() {
        GetPaymentMethodsTest test = new GetPaymentMethodsTest();
        test.testGetPaymentMethods();
    }

    @Test
    @Order(2)
    public void testCreatePaymentAccount() {
        CreatePaymentAccountTest test = new CreatePaymentAccountTest();
        test.testCreatePerfectMoneyUSDPaymentAccount();
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }

}
