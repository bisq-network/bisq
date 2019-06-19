package bisq.api.http;

import bisq.api.http.model.AuthForm;
import bisq.api.http.model.ChangePassword;
import bisq.api.http.model.payment.SepaPaymentAccount;

import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;



import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;

@RunWith(Arquillian.class)
public class ExperimentalFeatureIT {

    @DockerContainer
    Container alice = ContainerFactory.createApiContainer("alice", "8080->8080", 3333, false, false, false);

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
    }

    @InSequence(1)
    @Test
    public void createPaymentAccount_always_returns501() {
        SepaPaymentAccount accountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload();
        Response response = given().
                port(getAlicePort()).
                contentType(ContentType.JSON).
                body(accountToCreate).
                when().
                post("/api/v1/payment-accounts");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void removePaymentAccountById_always_returns501() {
        expect501(given().port(getAlicePort()).when().delete("/api/v1/payment-accounts/xyz"));
    }

    @InSequence(1)
    @Test
    public void searchPaymentAccounts_always_returns200() {
        given().port(getAlicePort()).when().get("/api/v1/payment-accounts").then().statusCode(200);
    }

    @InSequence(1)
    @Test
    public void getVersionDetails_always_returns200() {
        given().port(getAlicePort()).when().get("/api/v1/payment-accounts").then().statusCode(200);
    }

    @InSequence(1)
    @Test
    public void authenticate_always_returns501() {
//
        given().
                port(getAlicePort()).
                body(new AuthForm("abc")).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/user/authenticate").
//
        then().
                statusCode(200);
    }

    @InSequence(1)
    @Test
    public void changePassword_always_returns200() {
//
        given().
                port(getAlicePort()).
                body(new ChangePassword("abc", null)).
                contentType(ContentType.JSON).
//                
        when().
                post("/api/v1/user/password").
//
        then().
                statusCode(200);
    }

    private void expect501(Response response) {
        response.then().
                statusCode(501).
                body("errors[0]", equalTo("Experimental features disabled")).
                body("errors.size()", equalTo(1));
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }
}
