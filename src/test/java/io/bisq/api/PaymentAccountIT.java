package io.bisq.api;

import com.github.javafaker.Faker;
import io.bisq.api.model.SepaAccountToCreate;
import io.bisq.core.payment.payload.PaymentMethod;
import io.restassured.http.ContentType;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.ContainerBuilder;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isA;

@RunWith(Arquillian.class)
public class PaymentAccountIT {

    @DockerContainer
    Container alice = createApiContainer("alice", "8081->8080", 3333);

    private ContainerBuilder.ContainerOptionsBuilder withRegtestEnv(ContainerBuilder.ContainerOptionsBuilder builder) {
        return builder
                .withEnvironment("USE_LOCALHOST_FOR_P2P", "true")
                .withEnvironment("BASE_CURRENCY_NETWORK", "BTC_REGTEST")
                .withEnvironment("BTC_NODES", "bisq-bitcoin:18332")
                .withEnvironment("SEED_NODES", "bisq-seednode:8000")
                .withEnvironment("LOG_LEVEL", "debug");
    }

    private Container createApiContainer(String nameSuffix, String portBinding, int nodePort) {
        return withRegtestEnv(Container.withContainerName("bisq-api-" + nameSuffix).fromImage("bisq-api").withVolume("m2", "/root/.m2").withPortBinding(portBinding))
                .withEnvironment("NODE_PORT", nodePort)
                .withEnvironment("USE_DEV_PRIVILEGE_KEYS", true)
                .build();
    }

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
    public void createPaymentAccount_validData_returnsCreatedAccount() throws InterruptedException {
        final int alicePort = alice.getBindPort(8080);

        final SepaAccountToCreate accountToCreate = randomValidCreateSepaAccountPayload();

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
                and().body("holderName", equalTo(accountToCreate.holderName)).
                and().body("countryCode", equalTo(accountToCreate.countryCode)).
                and().body("bic", equalTo(accountToCreate.bic)).
                and().body("iban", equalTo(accountToCreate.iban)).
                and().body("size()", equalTo(7))
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
                and().body("paymentAccounts[0].holderName", equalTo(accountToCreate.holderName)).
                and().body("paymentAccounts[0].countryCode", equalTo(accountToCreate.countryCode)).
                and().body("paymentAccounts[0].bic", equalTo(accountToCreate.bic)).
                and().body("paymentAccounts[0].iban", equalTo(accountToCreate.iban)).
                and().body("paymentAccounts[0].size()", equalTo(7))
        ;
    }

    @Test
    public void createPaymentAccount_missingAccountName_returnsError() throws Exception {
        createPaymentAccount_missingAttributeTemplate("accountName", null);
        createPaymentAccount_missingAttributeTemplate("accountName", " ");
    }

    @Test
    public void createPaymentAccount_missingCountryCode_returnsError() throws Exception {
        createPaymentAccount_missingAttributeTemplate("countryCode", null);
        createPaymentAccount_missingAttributeTemplate("countryCode", " ");
    }

    @Test
    public void createPaymentAccount_missingHolderName_returnsError() throws Exception {
        createPaymentAccount_missingAttributeTemplate("holderName", null);
        createPaymentAccount_missingAttributeTemplate("holderName", " ");
    }

    @Test
    public void createPaymentAccount_missingBic_returnsError() throws Exception {
        createPaymentAccount_missingAttributeTemplate("bic", null);
        createPaymentAccount_missingAttributeTemplate("bic", " ");
    }

    @Test
    public void createPaymentAccount_missingIban_returnsError() throws Exception {
        createPaymentAccount_missingAttributeTemplate("iban", null);
        createPaymentAccount_missingAttributeTemplate("iban", " ");
    }

    @Test
    public void createPaymentAccount_invalidCountryCode_returnsError() throws Exception {
        createPaymentAccount_validationFailureTemplate("countryCode", "PLNX", 422, "countryCode is not valid country code");
    }

    @Test
    public void createPaymentAccount_invalidPaymentMethod_returnsError() throws Exception {
        createPaymentAccount_missingAttributeTemplate("paymentMethod", "");
    }

    private void createPaymentAccount_missingAttributeTemplate(String fieldName, Object fieldValue) throws Exception {
        createPaymentAccount_validationFailureTemplate(fieldName, fieldValue, 422, fieldName + " may not be empty");
    }

    private void createPaymentAccount_validationFailureTemplate(String fieldName, Object fieldValue, int expectedStatusCode, String expectedValidationMessage) throws Exception {
        final int alicePort = alice.getBindPort(8080);

        final SepaAccountToCreate accountToCreate = randomValidCreateSepaAccountPayload();
        SepaAccountToCreate.class.getField(fieldName).set(accountToCreate, fieldValue);

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

    private SepaAccountToCreate randomValidCreateSepaAccountPayload() {
        final Faker faker = new Faker();
        final SepaAccountToCreate accountToCreate = new SepaAccountToCreate();
        accountToCreate.paymentMethod = PaymentMethod.SEPA_ID;
        accountToCreate.accountName = faker.commerce().productName();
        accountToCreate.bic = faker.finance().bic();
        accountToCreate.iban = faker.finance().iban();
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.countryCode = faker.address().countryCode();
        return accountToCreate;
    }

}
