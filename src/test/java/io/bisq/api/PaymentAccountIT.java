package io.bisq.api;

import com.github.javafaker.Faker;
import io.bisq.api.model.payment.*;
import io.bisq.common.locale.CountryUtil;
import io.restassured.http.ContentType;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@RunWith(Arquillian.class)
public class PaymentAccountIT {

    @DockerContainer
    private Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, false);

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        /**
         * PaymentMethod initializes it's static values after all services get initialized
         */
        final int ALL_SERVICES_INITIALIZED_DELAY = 5000;
        Thread.sleep(ALL_SERVICES_INITIALIZED_DELAY);
    }

    @InSequence(1)
    @Test
    public void create_validSepa_returnsCreatedAccount() {
        final int alicePort = getAlicePort();

        final SepaPaymentAccount accountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload();

        final String expectedPaymentDetails = String.format("SEPA - Holder name: %s, IBAN: %s, BIC: %s, Country code: %s", accountToCreate.holderName, accountToCreate.iban, accountToCreate.bic, accountToCreate.countryCode);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("countryCode", equalTo(accountToCreate.countryCode)).
                and().body("bic", equalTo(accountToCreate.bic)).
                and().body("iban", equalTo(accountToCreate.iban)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("acceptedCountries", equalTo(accountToCreate.acceptedCountries)).
                and().body("size()", equalTo(11))
        ;

        given().
                port(alicePort).
//
        when().
                get("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("paymentAccounts.size()", equalTo(1)).
                and().body("paymentAccounts[0].id", isA(String.class)).
                and().body("paymentAccounts[0].paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("paymentAccounts[0].accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentAccounts[0].paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("paymentAccounts[0].holderName", equalTo(accountToCreate.holderName)).
                and().body("paymentAccounts[0].countryCode", equalTo(accountToCreate.countryCode)).
                and().body("paymentAccounts[0].bic", equalTo(accountToCreate.bic)).
                and().body("paymentAccounts[0].iban", equalTo(accountToCreate.iban)).
                and().body("paymentAccounts[0].selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("paymentAccounts[0].tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("paymentAccounts[0].acceptedCountries", equalTo(accountToCreate.acceptedCountries)).
                and().body("paymentAccounts[0].size()", equalTo(11))
        ;
    }

    @InSequence(1)
    @Test
    public void create_validAliPay_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final AliPayPaymentAccount accountToCreate = new AliPayPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountNr = faker.finance().iban();

        final String expectedPaymentDetails = String.format("AliPay - Account no.: %s", accountToCreate.accountNr);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountNr", equalTo(accountToCreate.accountNr)).
                and().body("size()", equalTo(7))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validCashApp_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final CashAppPaymentAccount accountToCreate = new CashAppPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.cashTag = faker.commerce().promotionCode();

        final String expectedPaymentDetails = String.format("CashApp - Account: %s", accountToCreate.cashTag);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("cashTag", equalTo(accountToCreate.cashTag)).
                and().body("size()", equalTo(7))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validCashDeposit_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final CashDepositPaymentAccount accountToCreate = new CashDepositPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountNr = faker.finance().iban();
        accountToCreate.accountType = faker.options().option("savings", "avista");
        accountToCreate.bankId = faker.finance().bic();
        accountToCreate.bankName = faker.company().name();
        accountToCreate.branchId = faker.company().buzzword();
        accountToCreate.countryCode = "DE";
        accountToCreate.holderEmail = faker.internet().emailAddress();
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.holderTaxId = faker.finance().creditCard();
        accountToCreate.requirements = faker.witcher().quote();

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", isA(String.class)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountNr", equalTo(accountToCreate.accountNr)).
                and().body("accountType", equalTo(accountToCreate.accountType)).
                and().body("bankId", equalTo(accountToCreate.bankId)).
                and().body("bankName", equalTo(accountToCreate.bankName)).
                and().body("branchId", equalTo(accountToCreate.branchId)).
                and().body("countryCode", equalTo(accountToCreate.countryCode)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("holderEmail", equalTo(accountToCreate.holderEmail)).
                and().body("holderTaxId", equalTo(accountToCreate.holderTaxId)).
                and().body("requirements", equalTo(accountToCreate.requirements)).
                and().body("size()", equalTo(16))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validChaseQuickPay_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final ChaseQuickPayPaymentAccount accountToCreate = new ChaseQuickPayPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.email = faker.internet().emailAddress();
        accountToCreate.holderName = faker.name().fullName();

        final String expectedPaymentDetails = String.format("Chase QuickPay - Holder name: %s, email: %s", accountToCreate.holderName, accountToCreate.email);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("email", equalTo(accountToCreate.email)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("size()", equalTo(8))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validCryptoCurrency_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final CryptoCurrencyPaymentAccount accountToCreate = new CryptoCurrencyPaymentAccount();
        accountToCreate.accountName = faker.commerce().productName();
        accountToCreate.selectedTradeCurrency = "BCH";
        accountToCreate.tradeCurrencies = Collections.singletonList(accountToCreate.selectedTradeCurrency);
        accountToCreate.address = "1ab616x3JxQsXsExCKX4iirdFwVDDXuwo";

        final String expectedPaymentDetails = String.format("Receivers altcoin address: %s", accountToCreate.address);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("address", equalTo(accountToCreate.address)).
                and().body("size()", equalTo(7))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validFasterPayments_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final FasterPaymentsPaymentAccount accountToCreate = new FasterPaymentsPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountNr = faker.finance().iban();
        accountToCreate.sortCode = faker.address().zipCode();

        final String expectedPaymentDetails = String.format("FasterPayments - UK Sort code: %s, Account number: %s", accountToCreate.sortCode, accountToCreate.accountNr);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountNr", equalTo(accountToCreate.accountNr)).
                and().body("sortCode", equalTo(accountToCreate.sortCode)).
                and().body("size()", equalTo(8))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validInteracETransfer_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final InteracETransferPaymentAccount accountToCreate = new InteracETransferPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.emailOrMobileNr = faker.internet().emailAddress();
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.question = faker.witcher().quote();
        accountToCreate.answer = faker.witcher().character();

        final String expectedPaymentDetails = String.format("Interac e-Transfer - Holder name: %s, email: %s, secret question: %s, answer: %s", accountToCreate.holderName, accountToCreate.emailOrMobileNr, accountToCreate.question, accountToCreate.answer);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("emailOrMobileNr", equalTo(accountToCreate.emailOrMobileNr)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("question", equalTo(accountToCreate.question)).
                and().body("answer", equalTo(accountToCreate.answer)).
                and().body("size()", equalTo(10))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validMoneyBeam_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final MoneyBeamPaymentAccount accountToCreate = new MoneyBeamPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountId = faker.idNumber().valid();

        final String expectedPaymentDetails = String.format("MoneyBeam - Account: %s", accountToCreate.accountId);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountId", equalTo(accountToCreate.accountId)).
                and().body("size()", equalTo(7))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validNationalBankAccount_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final NationalBankAccountPaymentAccount accountToCreate = new NationalBankAccountPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountNr = faker.finance().iban();
        accountToCreate.accountType = faker.options().option("savings", "avista");
        accountToCreate.bankId = faker.finance().bic();
        accountToCreate.bankName = faker.company().name();
        accountToCreate.branchId = faker.company().buzzword();
        accountToCreate.countryCode = "DE";
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.holderTaxId = faker.finance().creditCard();

        final String expectedPaymentDetails = String.format("National Bank transfer - Holder name: %s, Bank name: %s, Bank ID (BIC/SWIFT): %s, Branch no.: %s, Account no. (IBAN): %s, Country of bank: %s", accountToCreate.holderName, accountToCreate.bankName, accountToCreate.bankId, accountToCreate.branchId, accountToCreate.accountNr, CountryUtil.getNameByCode(accountToCreate.countryCode));

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountNr", equalTo(accountToCreate.accountNr)).
                and().body("accountType", equalTo(accountToCreate.accountType)).
                and().body("bankId", equalTo(accountToCreate.bankId)).
                and().body("bankName", equalTo(accountToCreate.bankName)).
                and().body("branchId", equalTo(accountToCreate.branchId)).
                and().body("countryCode", equalTo(accountToCreate.countryCode)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("holderTaxId", equalTo(accountToCreate.holderTaxId)).
                and().body("size()", equalTo(14))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validOKPay_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final OKPayPaymentAccount accountToCreate = new OKPayPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountNr = faker.idNumber().valid();

        final String expectedPaymentDetails = String.format("OKPay - Account no.: %s", accountToCreate.accountNr);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountNr", equalTo(accountToCreate.accountNr)).
                and().body("size()", equalTo(7))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validPerfectMoney_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final PerfectMoneyPaymentAccount accountToCreate = new PerfectMoneyPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountNr = faker.idNumber().valid();

        final String expectedPaymentDetails = String.format("PerfectMoney - Account no.: %s", accountToCreate.accountNr);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountNr", equalTo(accountToCreate.accountNr)).
                and().body("size()", equalTo(7))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validPopmoney_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final PopmoneyPaymentAccount accountToCreate = new PopmoneyPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountId = faker.idNumber().valid();
        accountToCreate.holderName = faker.name().fullName();

        final String expectedPaymentDetails = String.format("Popmoney - Holder name: %s, email or phone no.: %s", accountToCreate.holderName, accountToCreate.accountId);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountId", equalTo(accountToCreate.accountId)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("size()", equalTo(8))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validRevolut_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final RevolutPaymentAccount accountToCreate = new RevolutPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountId = faker.idNumber().valid();

        final String expectedPaymentDetails = String.format("Revolut - Account:%s", accountToCreate.accountId);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountId", equalTo(accountToCreate.accountId)).
                and().body("size()", equalTo(7))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validSameBankAccount_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final SameBankAccountPaymentAccount accountToCreate = new SameBankAccountPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountNr = faker.finance().iban();
        accountToCreate.accountType = faker.options().option("savings", "avista");
        accountToCreate.bankId = faker.finance().bic();
        accountToCreate.bankName = faker.company().name();
        accountToCreate.branchId = faker.company().buzzword();
        accountToCreate.countryCode = "PL";
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.holderTaxId = faker.finance().creditCard();

        final String expectedPaymentDetails = String.format("Transfer with same Bank - Holder name: %s, Bank name: %s, Bank ID (BIC/SWIFT): %s, Branch no.: %s, Account no. (IBAN): %s, Country of bank: %s", accountToCreate.holderName, accountToCreate.bankName, accountToCreate.bankId, accountToCreate.branchId, accountToCreate.accountNr, CountryUtil.getNameByCode(accountToCreate.countryCode));

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountNr", equalTo(accountToCreate.accountNr)).
                and().body("accountType", equalTo(accountToCreate.accountType)).
                and().body("bankId", equalTo(accountToCreate.bankId)).
                and().body("bankName", equalTo(accountToCreate.bankName)).
                and().body("branchId", equalTo(accountToCreate.branchId)).
                and().body("countryCode", equalTo(accountToCreate.countryCode)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("holderTaxId", equalTo(accountToCreate.holderTaxId)).
                and().body("size()", equalTo(14))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validSepaInstant_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final SepaInstantPaymentAccount accountToCreate = new SepaInstantPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountName = faker.commerce().productName();
        accountToCreate.bic = faker.finance().bic();
        accountToCreate.iban = faker.finance().iban();
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.countryCode = faker.address().countryCode();
        accountToCreate.acceptedCountries = Arrays.asList("PL", "GB");
        accountToCreate.selectedTradeCurrency = faker.options().option("PLN", "USD", "EUR", "GBP");
        accountToCreate.tradeCurrencies = Collections.singletonList(accountToCreate.selectedTradeCurrency);

        final String expectedPaymentDetails = String.format("SEPA Instant - Holder name: %s, IBAN: %s, BIC: %s, Country code: %s", accountToCreate.holderName, accountToCreate.iban, accountToCreate.bic, accountToCreate.countryCode);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("countryCode", equalTo(accountToCreate.countryCode)).
                and().body("bic", equalTo(accountToCreate.bic)).
                and().body("iban", equalTo(accountToCreate.iban)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("acceptedCountries", equalTo(accountToCreate.acceptedCountries)).
                and().body("size()", equalTo(11))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validSpecificBanks_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final SpecificBanksAccountPaymentAccount accountToCreate = new SpecificBanksAccountPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountNr = faker.finance().iban();
        accountToCreate.accountType = faker.options().option("savings", "avista");
        accountToCreate.bankId = faker.finance().bic();
        accountToCreate.bankName = faker.company().name();
        accountToCreate.branchId = faker.company().buzzword();
        accountToCreate.countryCode = "AT";
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.holderTaxId = faker.finance().creditCard();
        accountToCreate.acceptedBanks = Arrays.asList(faker.finance().bic(), faker.finance().bic());

        final String acceptedBanks = accountToCreate.acceptedBanks.stream().reduce((i, a) -> a.length() > 0 ? i + ", " + a : i).orElse("");
        final String expectedPaymentDetails = String.format("Transfers with specific banks - Holder name: %s, Bank name: %s, Bank ID (BIC/SWIFT): %s, Branch no.: %s, Account no. (IBAN): %s, Country of bank: %s, Accepted banks: %s", accountToCreate.holderName, accountToCreate.bankName, accountToCreate.bankId, accountToCreate.branchId, accountToCreate.accountNr, CountryUtil.getNameByCode(accountToCreate.countryCode), acceptedBanks);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountNr", equalTo(accountToCreate.accountNr)).
                and().body("accountType", equalTo(accountToCreate.accountType)).
                and().body("bankId", equalTo(accountToCreate.bankId)).
                and().body("bankName", equalTo(accountToCreate.bankName)).
                and().body("branchId", equalTo(accountToCreate.branchId)).
                and().body("countryCode", equalTo(accountToCreate.countryCode)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("holderTaxId", equalTo(accountToCreate.holderTaxId)).
                and().body("acceptedBanks", equalTo(accountToCreate.acceptedBanks)).
                and().body("size()", equalTo(15))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validSwish_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final SwishPaymentAccount accountToCreate = new SwishPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.mobileNr = faker.phoneNumber().cellPhone();

        final String expectedPaymentDetails = String.format("Swish - Holder name: %s, mobile no.: %s", accountToCreate.holderName, accountToCreate.mobileNr);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("mobileNr", equalTo(accountToCreate.mobileNr)).
                and().body("size()", equalTo(8))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validUphold_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final UpholdPaymentAccount accountToCreate = new UpholdPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.accountId = faker.idNumber().valid();

        final String expectedPaymentDetails = String.format("Uphold - Account: %s", accountToCreate.accountId);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("accountId", equalTo(accountToCreate.accountId)).
                and().body("size()", equalTo(7))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validUSPostalMoneyOrder_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final USPostalMoneyOrderPaymentAccount accountToCreate = new USPostalMoneyOrderPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.postalAddress = faker.address().fullAddress();

        final String expectedPaymentDetails = String.format("US Postal Money Order - Holder name: %s, postal address: %s", accountToCreate.holderName, accountToCreate.postalAddress);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("postalAddress", equalTo(accountToCreate.postalAddress)).
                and().body("size()", equalTo(8))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validVenmo_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final VenmoPaymentAccount accountToCreate = new VenmoPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.venmoUserName = faker.name().username();

        final String expectedPaymentDetails = String.format("Venmo - Holder name: %s, Venmo username: %s", accountToCreate.holderName, accountToCreate.venmoUserName);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("venmoUserName", equalTo(accountToCreate.venmoUserName)).
                and().body("size()", equalTo(8))
        ;
    }

    @InSequence(2)
    @Test
    public void create_validWesternUnion_returnsCreatedAccount() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final WesternUnionPaymentAccount accountToCreate = new WesternUnionPaymentAccount();
        ApiTestHelper.randomizeAccountPayload(accountToCreate);
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.city = faker.address().city();
        accountToCreate.countryCode = "DE";
        accountToCreate.email = faker.internet().emailAddress();
        accountToCreate.state = faker.address().state();

        final String expectedPaymentDetails = String.format("Western Union - Full name: %s, City: %s, County: %s, Email: %s", accountToCreate.holderName, accountToCreate.city, CountryUtil.getNameByCode(accountToCreate.countryCode), accountToCreate.email);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("id", isA(String.class)).
                and().body("paymentMethod", equalTo(accountToCreate.paymentMethod)).
                and().body("accountName", equalTo(accountToCreate.accountName)).
                and().body("paymentDetails", equalTo(expectedPaymentDetails)).
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("city", equalTo(accountToCreate.city)).
                and().body("countryCode", equalTo(accountToCreate.countryCode)).
                and().body("email", equalTo(accountToCreate.email)).
                and().body("state", equalTo(accountToCreate.state)).
                and().body("size()", equalTo(11))
        ;
    }

    @InSequence(3)
    @Test
    public void removeById_existingAccount_returns204() {
        final int alicePort = getAlicePort();

        final List<String> accountList = given().
                port(alicePort).
                contentType(ContentType.JSON).
                when().
                get("/api/v1/payment-accounts/").
                getBody().jsonPath().get("paymentAccounts.id");

        Assert.assertThat(accountList, not(empty()));

        accountList.forEach(id -> given().port(alicePort).when().delete("/api/v1/payment-accounts/" + id).then().statusCode(204));

        given().
                port(alicePort).
//
        when().
                get("/api/v1/payment-accounts").
//
        then().
                statusCode(200).
                and().body("paymentAccounts.size()", equalTo(0))
        ;
    }

    @InSequence(4)
    @Test
    public void removeById_nonExistingAccount_returns404() {
        final int alicePort = getAlicePort();

        given().
                port(alicePort).
//
        when().
                delete("/api/v1/payment-accounts/abc").
//
        then().
                statusCode(404);
    }

    @Test
    public void create_missingAccountName_returnsError() throws Exception {
        create_missingAttributeTemplate("accountName", null);
        create_missingAttributeTemplate("accountName", " ");
    }

    @InSequence(1)
    @Test
    public void create_unsupportedCryptoSelectedTradeCurrency_returnsError() throws Exception {
        create_cryptoValidationFailureTemplate("selectedTradeCurrency", "BCHX", 422, "Unsupported crypto currency code: BCHX");
    }

    @InSequence(1)
    @Test
    public void create_unsupportedCryptoTradeCurrency_returnsError() throws Exception {
        create_cryptoValidationFailureTemplate("tradeCurrencies", Collections.singletonList("XYZ"), 422, "Unsupported crypto currency code: XYZ");
    }

    @InSequence(1)
    @Test
    public void create_invalidCryptoAddress_returnsError() throws Exception {
        create_cryptoValidationFailureTemplate("address", "abc", 422, "Address is not a valid BCH address! Input too short");
    }

    @Test
    public void create_missingCountryCode_returnsError() throws Exception {
        create_missingAttributeTemplate("countryCode", null);
        create_missingAttributeTemplate("countryCode", " ");
    }

    @Test
    public void create_missingHolderName_returnsError() throws Exception {
        create_missingAttributeTemplate("holderName", null);
        create_missingAttributeTemplate("holderName", " ");
    }

    @Test
    public void create_missingBic_returnsError() throws Exception {
        create_missingAttributeTemplate("bic", null);
        create_missingAttributeTemplate("bic", " ");
    }

    @Test
    public void create_missingIban_returnsError() throws Exception {
        create_missingAttributeTemplate("iban", null);
        create_missingAttributeTemplate("iban", " ");
    }

    @Test
    public void create_invalidCountryCode_returnsError() throws Exception {
        create_sepaValidationFailureTemplate("countryCode", "PLNX", 422, "countryCode is not valid country code");
    }

    @Test
    public void create_invalidPaymentMethod_returnsError() throws Exception {
        final int alicePort = getAlicePort();

        final PaymentAccount accountToCreate = new PaymentAccount("") {
        };
        ApiTestHelper.randomizeAccountPayload(accountToCreate);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(422).
                and().body("errors.size()", equalTo(1)).
                and().body("errors[0]", equalTo("Unable to recognize sub type of PaymentAccount. Value 'null' is invalid. Allowed values are: ALI_PAY, CASH_APP, CASH_DEPOSIT, CHASE_QUICK_PAY, CLEAR_X_CHANGE, BLOCK_CHAINS, FASTER_PAYMENTS, INTERAC_E_TRANSFER, MONEY_BEAM, NATIONAL_BANK, OK_PAY, PERFECT_MONEY, POPMONEY, REVOLUT, SAME_BANK, SEPA, SEPA_INSTANT, SPECIFIC_BANKS, SWISH, UPHOLD, US_POSTAL_MONEY_ORDER, VENMO, WESTERN_UNION"))
        ;
    }

    private void create_missingAttributeTemplate(String fieldName, Object fieldValue) throws Exception {
        create_sepaValidationFailureTemplate(fieldName, fieldValue, 422, fieldName + " may not be empty");
    }

    private void create_sepaValidationFailureTemplate(String fieldName, Object fieldValue, int expectedStatusCode, String expectedValidationMessage) throws Exception {
        final int alicePort = getAlicePort();

        final SepaPaymentAccount accountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload();
        SepaPaymentAccount.class.getField(fieldName).set(accountToCreate, fieldValue);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(expectedStatusCode).
                and().body("errors", hasItem(expectedValidationMessage))
        ;
    }

    private void create_cryptoValidationFailureTemplate(String fieldName, Object fieldValue, int expectedStatusCode, String expectedValidationMessage) throws Exception {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();

        final CryptoCurrencyPaymentAccount accountToCreate = new CryptoCurrencyPaymentAccount();
        accountToCreate.accountName = faker.commerce().productName();
        accountToCreate.selectedTradeCurrency = "BCH";
        accountToCreate.tradeCurrencies = Collections.singletonList(accountToCreate.selectedTradeCurrency);
        accountToCreate.address = "1ab616x3JxQsXsExCKX4iirdFwVDDXuwo";
        CryptoCurrencyPaymentAccount.class.getField(fieldName).set(accountToCreate, fieldValue);

        given().
                port(alicePort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                post("/api/v1/payment-accounts").
//
        then().
                statusCode(expectedStatusCode).
                and().body("errors", hasItem(expectedValidationMessage))
        ;
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
