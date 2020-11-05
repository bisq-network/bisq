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

package bisq.core.api.model;

import bisq.core.locale.Res;
import bisq.core.payment.ChaseQuickPayAccount;
import bisq.core.payment.ClearXchangeAccount;
import bisq.core.payment.F2FAccount;
import bisq.core.payment.HalCashAccount;
import bisq.core.payment.JapanBankAccount;
import bisq.core.payment.NationalBankAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.SepaAccount;
import bisq.core.payment.USPostalMoneyOrderAccount;
import bisq.core.payment.payload.BankAccountPayload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import org.junit.Before;
import org.junit.Test;

import static bisq.core.payment.payload.PaymentMethod.*;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Slf4j
public class PaymentAccountFormTest {

    private static final String PROPERTY_NAME_COMMENT = "_COMMENT_";
    private static final String PROPERTY_VALUE_COMMENT = "Please do not edit the paymentMethodId field.";

    private static final String PROPERTY_NAME_PAYMENT_METHOD_ID = "paymentMethodId";

    private static final String PROPERTY_NAME_ACCOUNT_NAME = "accountName";
    private static final String PROPERTY_NAME_ACCOUNT_NR = "accountNr";
    private static final String PROPERTY_NAME_ACCOUNT_TYPE = "accountType";
    private static final String PROPERTY_NAME_BANK_ACCOUNT_NAME = "bankAccountName";
    private static final String PROPERTY_NAME_BANK_ACCOUNT_NUMBER = "bankAccountNumber";
    private static final String PROPERTY_NAME_BANK_ACCOUNT_TYPE = "bankAccountType";
    private static final String PROPERTY_NAME_BANK_BRANCH_CODE = "bankBranchCode";
    private static final String PROPERTY_NAME_BANK_BRANCH_NAME = "bankBranchName";
    private static final String PROPERTY_NAME_BANK_CODE = "bankCode";
    @SuppressWarnings("unused")
    private static final String PROPERTY_NAME_BANK_ID = "bankId";
    private static final String PROPERTY_NAME_BANK_NAME = "bankName";
    private static final String PROPERTY_NAME_BRANCH_ID = "branchId";
    private static final String PROPERTY_NAME_BIC = "bic";
    private static final String PROPERTY_NAME_COUNTRY = "country";
    private static final String PROPERTY_NAME_CITY = "city";
    private static final String PROPERTY_NAME_CONTACT = "contact";
    private static final String PROPERTY_NAME_EMAIL = "email";
    private static final String PROPERTY_NAME_EMAIL_OR_MOBILE_NR = "emailOrMobileNr";
    private static final String PROPERTY_NAME_EXTRA_INFO = "extraInfo";
    private static final String PROPERTY_NAME_HOLDER_NAME = "holderName";
    private static final String PROPERTY_NAME_HOLDER_TAX_ID = "holderTaxId";
    private static final String PROPERTY_NAME_IBAN = "iban";
    private static final String PROPERTY_NAME_MOBILE_NR = "mobileNr";
    private static final String PROPERTY_NAME_NATIONAL_ACCOUNT_ID = "nationalAccountId";
    private static final String PROPERTY_NAME_POSTAL_ADDRESS = "postalAddress";

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();


    // The payment account serializer / deserializer.
    private static final PaymentAccountForm paymentAccountForm = new PaymentAccountForm();

    private static final Map<String, Object> EXPECTED_FORM = new HashMap<>();

    @Before
    public void setup() {
        Res.setup();
        EXPECTED_FORM.clear();
    }

    @Test
    public void testBrazilNationalBankAccountForm() {
        File emptyForm = paymentAccountForm.getPaymentAccountForm(NATIONAL_BANK_ID);
        log.info("Empty form saved to {}", paymentAccountForm.getClickableURI(emptyForm));
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
        log.info("Completed form: {}", paymentAccountForm.toJsonString(completedForm));

        NationalBankAccount paymentAccount = (NationalBankAccount) paymentAccountForm.toPaymentAccount(completedForm);
        verifyAccountFiatCurrency(paymentAccount, "BRL");
        verifyCommonFormEntries(paymentAccount);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_COUNTRY),
                Objects.requireNonNull(paymentAccount.getCountry()).code);

        BankAccountPayload payload = (BankAccountPayload) paymentAccount.getPaymentAccountPayload();
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_ACCOUNT_NR), payload.getAccountNr());
        // When no BankId is required, getBankId() returns bankName.
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BANK_NAME), payload.getBankId());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BANK_NAME), payload.getBankName());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BRANCH_ID), payload.getBranchId());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_HOLDER_NAME), payload.getHolderName());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_HOLDER_TAX_ID), payload.getHolderTaxId());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_NATIONAL_ACCOUNT_ID), payload.getNationalAccountId());
        // log.info("Deserialized {}: {}", paymentAccount.getClass().getSimpleName(), paymentAccount);
    }

    @Test
    public void testChaseQuickPayAccountForm() {
        File emptyForm = paymentAccountForm.getPaymentAccountForm(CHASE_QUICK_PAY_ID);
        log.info("Empty form saved to {}", paymentAccountForm.getClickableURI(emptyForm));
        verifyEmptyForm(emptyForm,
                CHASE_QUICK_PAY_ID,
                PROPERTY_NAME_EMAIL,
                PROPERTY_NAME_HOLDER_NAME);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, CHASE_QUICK_PAY_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Quick Pay Acct");
        EXPECTED_FORM.put(PROPERTY_NAME_EMAIL, "johndoe@quickpay.com");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_NAME, "John Doe");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", paymentAccountForm.toJsonString(completedForm));

        ChaseQuickPayAccount paymentAccount = (ChaseQuickPayAccount) paymentAccountForm.toPaymentAccount(completedForm);
        verifyAccountFiatCurrency(paymentAccount, "USD");
        verifyCommonFormEntries(paymentAccount);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_EMAIL), paymentAccount.getEmail());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_HOLDER_NAME), paymentAccount.getHolderName());
        // log.info("Deserialized {}: {}", paymentAccount.getClass().getSimpleName(), paymentAccount);
    }

    @Test
    public void testClearXChangeAccountForm() {
        File emptyForm = paymentAccountForm.getPaymentAccountForm(CLEAR_X_CHANGE_ID);
        log.info("Empty form saved to {}", paymentAccountForm.getClickableURI(emptyForm));
        verifyEmptyForm(emptyForm,
                CLEAR_X_CHANGE_ID,
                PROPERTY_NAME_EMAIL_OR_MOBILE_NR,
                PROPERTY_NAME_HOLDER_NAME);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, CLEAR_X_CHANGE_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "USD Zelle Account");
        EXPECTED_FORM.put(PROPERTY_NAME_EMAIL_OR_MOBILE_NR, "jane@doe.com");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_NAME, "Jane Doe");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", paymentAccountForm.toJsonString(completedForm));

        ClearXchangeAccount paymentAccount = (ClearXchangeAccount) paymentAccountForm.toPaymentAccount(completedForm);
        verifyCommonFormEntries(paymentAccount);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_EMAIL_OR_MOBILE_NR), paymentAccount.getEmailOrMobileNr());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_HOLDER_NAME), paymentAccount.getHolderName());
        // log.info("Deserialized {}: {}", paymentAccount.getClass().getSimpleName(), paymentAccount);
        verifyAccountFiatCurrency(paymentAccount, "USD");
    }

    @Test
    public void testF2FAccountForm() {
        File emptyForm = paymentAccountForm.getPaymentAccountForm(F2F_ID);
        log.info("Empty form saved to {}", paymentAccountForm.getClickableURI(emptyForm));
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
        log.info("Completed form: {}", paymentAccountForm.toJsonString(completedForm));

        F2FAccount paymentAccount = (F2FAccount) paymentAccountForm.toPaymentAccount(completedForm);
        verifyAccountFiatCurrency(paymentAccount, "BRL");
        verifyCommonFormEntries(paymentAccount);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_COUNTRY),
                Objects.requireNonNull(paymentAccount.getCountry()).code);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_CITY), paymentAccount.getCity());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_CONTACT), paymentAccount.getContact());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_EXTRA_INFO), paymentAccount.getExtraInfo());
        // log.info("Deserialized {}: {}", paymentAccount.getClass().getSimpleName(), paymentAccount);
    }

    @Test
    public void testHalCashAccountForm() {
        File emptyForm = paymentAccountForm.getPaymentAccountForm(HAL_CASH_ID);
        log.info("Empty form saved to {}", paymentAccountForm.getClickableURI(emptyForm));
        verifyEmptyForm(emptyForm,
                HAL_CASH_ID,
                PROPERTY_NAME_MOBILE_NR);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, HAL_CASH_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Hal Cash Acct");
        EXPECTED_FORM.put(PROPERTY_NAME_MOBILE_NR, "798 123 456");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", paymentAccountForm.toJsonString(completedForm));

        HalCashAccount paymentAccount = (HalCashAccount) paymentAccountForm.toPaymentAccount(completedForm);
        verifyAccountFiatCurrency(paymentAccount, "EUR");
        verifyCommonFormEntries(paymentAccount);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_MOBILE_NR), paymentAccount.getMobileNr());
        // log.info("Deserialized {}: {}", paymentAccount.getClass().getSimpleName(), paymentAccount);
    }

    @Test
    public void testJapanBankAccountForm() {
        File emptyForm = paymentAccountForm.getPaymentAccountForm(JAPAN_BANK_ID);
        log.info("Empty form saved to {}", paymentAccountForm.getClickableURI(emptyForm));
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
        log.info("Completed form: {}", paymentAccountForm.toJsonString(completedForm));

        JapanBankAccount paymentAccount = (JapanBankAccount) paymentAccountForm.toPaymentAccount(completedForm);
        verifyAccountFiatCurrency(paymentAccount, "JPY");
        verifyCommonFormEntries(paymentAccount);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BANK_CODE), paymentAccount.getBankCode());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BANK_NAME), paymentAccount.getBankName());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BANK_BRANCH_CODE), paymentAccount.getBankBranchCode());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BANK_BRANCH_NAME), paymentAccount.getBankBranchName());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BANK_ACCOUNT_NAME), paymentAccount.getBankAccountName());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BANK_ACCOUNT_TYPE), paymentAccount.getBankAccountType());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BANK_ACCOUNT_NUMBER), paymentAccount.getBankAccountNumber());
        // log.info("Deserialized {}: {}", paymentAccount.getClass().getSimpleName(), paymentAccount);
    }

    @Test
    public void testSepaAccountForm() {
        File emptyForm = paymentAccountForm.getPaymentAccountForm(SEPA_ID);
        log.info("Empty form saved to {}", paymentAccountForm.getClickableURI(emptyForm));
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
        log.info("Completed form: {}", paymentAccountForm.toJsonString(completedForm));

        SepaAccount paymentAccount = (SepaAccount) paymentAccountForm.toPaymentAccount(completedForm);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_COUNTRY), Objects.requireNonNull(paymentAccount.getCountry()).code);
        verifyAccountFiatCurrency(paymentAccount, "EUR");
        verifyCommonFormEntries(paymentAccount);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_HOLDER_NAME), paymentAccount.getHolderName());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_IBAN), paymentAccount.getIban());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BIC), paymentAccount.getBic());
        // bankId == bic
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_BIC), paymentAccount.getBankId());
        // log.info("Deserialized {}: {}", paymentAccount.getClass().getSimpleName(), paymentAccount);
    }

    @Test
    public void testUSPostalMoneyOrderAccountForm() {
        File emptyForm = paymentAccountForm.getPaymentAccountForm(US_POSTAL_MONEY_ORDER_ID);
        log.info("Empty form saved to {}", paymentAccountForm.getClickableURI(emptyForm));
        verifyEmptyForm(emptyForm,
                US_POSTAL_MONEY_ORDER_ID,
                PROPERTY_NAME_HOLDER_NAME,
                PROPERTY_NAME_POSTAL_ADDRESS);

        EXPECTED_FORM.put(PROPERTY_NAME_PAYMENT_METHOD_ID, US_POSTAL_MONEY_ORDER_ID);
        EXPECTED_FORM.put(PROPERTY_NAME_ACCOUNT_NAME, "Bubba's Acct");
        EXPECTED_FORM.put(PROPERTY_NAME_HOLDER_NAME, "Bubba");
        EXPECTED_FORM.put(PROPERTY_NAME_POSTAL_ADDRESS, "100 Westwood Terrace Austin, TX 78701");

        File completedForm = fillPaymentAccountForm();
        log.info("Completed form: {}", paymentAccountForm.toJsonString(completedForm));

        USPostalMoneyOrderAccount paymentAccount = (USPostalMoneyOrderAccount) paymentAccountForm.toPaymentAccount(completedForm);
        verifyAccountFiatCurrency(paymentAccount, "USD");
        verifyCommonFormEntries(paymentAccount);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_HOLDER_NAME), paymentAccount.getHolderName());
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_POSTAL_ADDRESS), paymentAccount.getPostalAddress());
        // log.info("Deserialized {}: {}", paymentAccount.getClass().getSimpleName(), paymentAccount);
    }

    // Private

    private void verifyCommonFormEntries(PaymentAccount paymentAccount) {
        assertNotNull(paymentAccount);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_PAYMENT_METHOD_ID), paymentAccount.getPaymentMethod().getId());
        assertTrue(paymentAccount.getCreationDate().getTime() > 0);
        assertEquals(EXPECTED_FORM.get(PROPERTY_NAME_ACCOUNT_NAME), paymentAccount.getAccountName());
    }

    private void verifyEmptyForm(File jsonForm, String paymentMethodId, String... fields) {
        @SuppressWarnings("unchecked")
        Map<String, Object> emptyForm = (Map<String, Object>) gson.fromJson(
                paymentAccountForm.toJsonString(jsonForm),
                Object.class);
        assertNotNull(emptyForm);
        assertEquals(PROPERTY_VALUE_COMMENT, emptyForm.get(PROPERTY_NAME_COMMENT));
        assertEquals(paymentMethodId, emptyForm.get(PROPERTY_NAME_PAYMENT_METHOD_ID));
        assertEquals("Your accountname", emptyForm.get(PROPERTY_NAME_ACCOUNT_NAME));
        for (String field : fields) {
            assertEquals("Your " + field.toLowerCase(), emptyForm.get(field));
        }
    }

    private void verifyAccountFiatCurrency(PaymentAccount paymentAccount, String expectedCurrencyCode) {
        assertEquals(expectedCurrencyCode, Objects.requireNonNull(paymentAccount.getSingleTradeCurrency()).getCode());
    }

    private File fillPaymentAccountForm() {
        File f = new File(getProperty("java.io.tmpdir"), "tmp.json");
        try {
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(f), UTF_8));
            writer.beginObject();
            writer.name(PROPERTY_NAME_COMMENT);
            writer.value(PROPERTY_VALUE_COMMENT);
            for (Map.Entry<String, Object> entry : EXPECTED_FORM.entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();
                writer.name(k);
                writer.value(v.toString());
            }
            writer.endObject();
            writer.close();
        } catch (IOException ex) {
            log.error("", ex);
            fail(format("Could not write json file from form entries %s", EXPECTED_FORM));
        }
        return f;
    }
}
