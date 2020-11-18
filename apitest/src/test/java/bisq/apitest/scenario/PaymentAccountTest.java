package bisq.apitest.scenario;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
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
            // setUpScaffold(bitcoind, seednode, arbdaemon, alicedaemon);
            setUpScaffold(alicedaemon);
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
    public void testCreatePaymentAccount(TestInfo testInfo) {
        CreatePaymentAccountTest test = new CreatePaymentAccountTest();

        test.testCreateAustraliaPayidAccount(testInfo);
        test.testBrazilNationalBankAccountForm(testInfo);
        test.testChaseQuickPayAccountForm(testInfo);
        test.testClearXChangeAccountForm(testInfo);
        test.testF2FAccountForm(testInfo);
        test.testHalCashAccountForm(testInfo);
        test.testJapanBankAccountForm(testInfo);
        test.testSepaAccountForm(testInfo);
        test.testSwishAccountForm(testInfo);
        test.testUSPostalMoneyOrderAccountForm(testInfo);

        test.testDeprecatedCreatePerfectMoneyUSDPaymentAccount();
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }

}
