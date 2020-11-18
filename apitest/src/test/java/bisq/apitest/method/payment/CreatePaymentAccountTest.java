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

package bisq.apitest.method.payment;

import bisq.proto.grpc.GetPaymentAccountsRequest;

import protobuf.PaymentAccount;
import protobuf.PerfectMoneyAccountPayload;

import java.io.File;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.core.payment.payload.PaymentMethod.*;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@Disabled
@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class CreatePaymentAccountTest extends AbstractPaymentAccountTest {

    // TODO Test PaymentAccountForm's PaymentAccount toPaymentAccount(File jsonForm)
    //  after replacement api method 'createpaymentacct' is implemented.

    @BeforeAll
    public static void setUp() {
        try {
            setUpScaffold(bitcoind, alicedaemon);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(1)
    public void testCreateAustraliaPayidAccount(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, AUSTRALIA_PAYID_ID);
        verifyEmptyForm(emptyForm,
                AUSTRALIA_PAYID_ID,
                PROPERTY_NAME_BANK_ACCOUNT_NAME);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, AUSTRALIA_PAYID_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Australia Pay ID Account");
        EXPECTED_FORM.put(PROPERTY_NAME_PAY_ID, "123 456 789");
        EXPECTED_FORM.put(PROPERTY_NAME_BANK_ACCOUNT_NAME, "Credit Union Australia");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));
    }

    @Test
    public void testBrazilNationalBankAccountForm(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, NATIONAL_BANK_ID);
        verifyEmptyForm(emptyForm,
                NATIONAL_BANK_ID,
                PROPERTY_NAME_ACCOUNT_NR,
                PROPERTY_NAME_ACCOUNT_TYPE,
                PROPERTY_NAME_BANK_NAME,
                PROPERTY_NAME_BRANCH_ID,
                PROPERTY_NAME_COUNTRY,
                PROPERTY_NAME_HOLDER_NAME,
                PROPERTY_NAME_HOLDER_TAX_ID,
                PROPERTY_NAME_NATIONAL_ACCOUNT_ID);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, NATIONAL_BANK_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Banco do Brasil");
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NR, "456789-87");
        // No BankId is required for BR.
        EXPECTED_FORM.put(PROPERTY_NAME_BANK_NAME, "Banco do Brasil");
        EXPECTED_FORM.put(PROPERTY_NAME_BRANCH_ID, "456789-10");
        EXPECTED_FORM.put(PROPERTY_NAME_COUNTRY, "BR");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_NAME, "Joao da Silva");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_TAX_ID, "123456789");
        EXPECTED_FORM.put(PROPERTY_NAME_NATIONAL_ACCOUNT_ID, "123456789");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));

    }

    @Test
    public void testChaseQuickPayAccountForm(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, CHASE_QUICK_PAY_ID);
        verifyEmptyForm(emptyForm,
                CHASE_QUICK_PAY_ID,
                PROPERTY_NAME_EMAIL,
                PROPERTY_NAME_HOLDER_NAME);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, CHASE_QUICK_PAY_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Quick Pay Acct");
        EXPECTED_FORM.put(PROPERTY_NAME_EMAIL, "johndoe@quickpay.com");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_NAME, "John Doe");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));

    }

    @Test
    public void testClearXChangeAccountForm(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, CLEAR_X_CHANGE_ID);
        verifyEmptyForm(emptyForm,
                CLEAR_X_CHANGE_ID,
                PROPERTY_NAME_EMAIL_OR_MOBILE_NR,
                PROPERTY_NAME_HOLDER_NAME);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, CLEAR_X_CHANGE_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "USD Zelle Account");
        EXPECTED_FORM.put(PROPERTY_NAME_EMAIL_OR_MOBILE_NR, "jane@doe.com");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_NAME, "Jane Doe");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));
    }

    @Test
    public void testF2FAccountForm(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, F2F_ID);
        verifyEmptyForm(emptyForm,
                F2F_ID,
                PROPERTY_NAME_COUNTRY,
                PROPERTY_NAME_CITY,
                PROPERTY_NAME_CONTACT,
                PROPERTY_NAME_EXTRA_INFO);


        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, F2F_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Conta Cara a Cara");
        EXPECTED_FORM.put(PROPERTY_NAME_COUNTRY, "BR");
        EXPECTED_FORM.put(PROPERTY_NAME_CITY, "Rio de Janeiro");
        EXPECTED_FORM.put(PROPERTY_NAME_CONTACT, "Freddy Beira Mar");
        EXPECTED_FORM.put(PROPERTY_NAME_EXTRA_INFO, "So fim de semana");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));
    }

    @Test
    public void testHalCashAccountForm(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, HAL_CASH_ID);
        verifyEmptyForm(emptyForm,
                HAL_CASH_ID,
                PROPERTY_NAME_MOBILE_NR);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, HAL_CASH_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Hal Cash Acct");
        EXPECTED_FORM.put(PROPERTY_NAME_MOBILE_NR, "798 123 456");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));
    }

    @Test
    public void testJapanBankAccountForm(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, JAPAN_BANK_ID);
        verifyEmptyForm(emptyForm,
                JAPAN_BANK_ID,
                PROPERTY_NAME_BANK_NAME,
                PROPERTY_NAME_BANK_CODE,
                PROPERTY_NAME_BANK_BRANCH_CODE,
                PROPERTY_NAME_BANK_BRANCH_NAME,
                PROPERTY_NAME_BANK_ACCOUNT_NAME,
                PROPERTY_NAME_BANK_ACCOUNT_TYPE,
                PROPERTY_NAME_BANK_ACCOUNT_NUMBER);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, JAPAN_BANK_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Fukuoka Account");
        EXPECTED_FORM.put(PROPERTY_NAME_BANK_NAME, "Bank of Kyoto");
        EXPECTED_FORM.put(PROPERTY_NAME_BANK_CODE, "FKBKJPJT");
        EXPECTED_FORM.put(PROPERTY_NAME_BANK_BRANCH_CODE, "8100-8727");
        EXPECTED_FORM.put(PROPERTY_NAME_BANK_BRANCH_NAME, "Fukuoka Branch");
        EXPECTED_FORM.put(PROPERTY_NAME_BANK_ACCOUNT_NAME, "Fukuoka Account");
        EXPECTED_FORM.put(PROPERTY_NAME_BANK_ACCOUNT_TYPE, "Yen Account");
        EXPECTED_FORM.put(PROPERTY_NAME_BANK_ACCOUNT_NUMBER, "8100-8727-0000");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));
    }

    @Test
    public void testSepaAccountForm(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, SEPA_ID);
        verifyEmptyForm(emptyForm,
                SEPA_ID,
                PROPERTY_NAME_COUNTRY,
                PROPERTY_NAME_HOLDER_NAME,
                PROPERTY_NAME_IBAN,
                PROPERTY_NAME_BIC);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, SEPA_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Conta Sepa");
        EXPECTED_FORM.put(PROPERTY_NAME_COUNTRY, "PT");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_NAME, "Jose da Silva");
        EXPECTED_FORM.put(PROPERTY_NAME_IBAN, "909-909");
        EXPECTED_FORM.put(PROPERTY_NAME_BIC, "909");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));
    }

    @Test
    public void testSwishAccountForm(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, SWISH_ID);
        verifyEmptyForm(emptyForm,
                SWISH_ID,
                PROPERTY_NAME_MOBILE_NR,
                PROPERTY_NAME_HOLDER_NAME);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, SWISH_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Swish Account");
        EXPECTED_FORM.put(PROPERTY_NAME_MOBILE_NR, "+46 7 6060 0101");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_NAME, "Swish Account Holder");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));

    }

    @Test
    public void testUSPostalMoneyOrderAccountForm(TestInfo testInfo) {
        File emptyForm = getEmptyForm(testInfo, US_POSTAL_MONEY_ORDER_ID);
        verifyEmptyForm(emptyForm,
                US_POSTAL_MONEY_ORDER_ID,
                PROPERTY_NAME_HOLDER_NAME,
                PROPERTY_NAME_POSTAL_ADDRESS);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, US_POSTAL_MONEY_ORDER_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Bubba's Acct");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_NAME, "Bubba");
        EXPECTED_FORM.put(PROPERTY_NAME_POSTAL_ADDRESS, "000 Westwood Terrace Austin, TX 78700");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", PAYMENT_ACCOUNT_FORM.toJsonString(completedForm));
    }

    @Test
    public void testDeprecatedCreatePerfectMoneyUSDPaymentAccount() {
        String PERFECT_MONEY_ACCT_NAME = "Perfect Money USD";
        String PERFECT_MONEY_ACCT_NUMBER = "0123456789";

        var perfectMoneyPaymentAccountRequest = createCreatePerfectMoneyPaymentAccountRequest(
                PERFECT_MONEY_ACCT_NAME,
                PERFECT_MONEY_ACCT_NUMBER,
                "USD");
        //noinspection ResultOfMethodCallIgnored
        grpcStubs(alicedaemon).paymentAccountsService.createPaymentAccount(perfectMoneyPaymentAccountRequest);

        var getPaymentAccountsRequest = GetPaymentAccountsRequest.newBuilder().build();
        var reply = grpcStubs(alicedaemon).paymentAccountsService.getPaymentAccounts(getPaymentAccountsRequest);

        // The daemon is running against the regtest/dao setup files, and was set up with
        // two dummy accounts ("PerfectMoney dummy", "ETH dummy") before any tests ran.
        // We just added 1 test account, making 3 total.
        assertEquals(3, reply.getPaymentAccountsCount());

        // Sort the returned list by creation date; the last item in the sorted
        // list will be the payment acct we just created.
        List<PaymentAccount> paymentAccountList = reply.getPaymentAccountsList().stream()
                .sorted(comparing(PaymentAccount::getCreationDate))
                .collect(Collectors.toList());
        PaymentAccount paymentAccount = paymentAccountList.get(2);
        PerfectMoneyAccountPayload perfectMoneyAccount = paymentAccount
                .getPaymentAccountPayload()
                .getPerfectMoneyAccountPayload();

        assertEquals(PERFECT_MONEY_ACCT_NAME, paymentAccount.getAccountName());
        assertEquals("USD",
                paymentAccount.getSelectedTradeCurrency().getFiatCurrency().getCurrency().getCurrencyCode());
        assertEquals(PERFECT_MONEY_ACCT_NUMBER, perfectMoneyAccount.getAccountNr());
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
