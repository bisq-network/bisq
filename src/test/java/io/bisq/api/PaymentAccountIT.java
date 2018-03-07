package io.bisq.api;

import io.bisq.api.model.SepaAccountToCreate;
import io.restassured.http.ContentType;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

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
    public void create_validData_returnsCreatedAccount() {
        final int alicePort = getAlicePort();

        final SepaAccountToCreate accountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload();

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
                and().body("selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("size()", equalTo(9))
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
                and().body("paymentAccounts[0].selectedTradeCurrency", equalTo(accountToCreate.selectedTradeCurrency)).
                and().body("paymentAccounts[0].tradeCurrencies", equalTo(accountToCreate.tradeCurrencies)).
                and().body("paymentAccounts[0].size()", equalTo(9))
        ;
    }

    @InSequence(2)
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

    @InSequence(3)
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
        create_validationFailureTemplate("countryCode", "PLNX", 422, "countryCode is not valid country code");
    }

    @Test
    public void create_invalidPaymentMethod_returnsError() throws Exception {
        create_missingAttributeTemplate("paymentMethod", "");
    }

    private void create_missingAttributeTemplate(String fieldName, Object fieldValue) throws Exception {
        create_validationFailureTemplate(fieldName, fieldValue, 422, fieldName + " may not be empty");
    }

    private void create_validationFailureTemplate(String fieldName, Object fieldValue, int expectedStatusCode, String expectedValidationMessage) throws Exception {
        final int alicePort = getAlicePort();

        final SepaAccountToCreate accountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload();
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

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }


}
