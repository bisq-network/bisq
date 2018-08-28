package network.bisq.api;

import com.github.javafaker.Faker;
import network.bisq.api.model.AuthForm;
import network.bisq.api.model.AuthResult;
import network.bisq.api.model.ChangePassword;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.isA;

@RunWith(Arquillian.class)
public class UserResourceIT {

    @DockerContainer
    Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, false);

    private static String validPassword = new Faker().internet().password();
    private static String invalidPassword = validPassword + validPassword;
    private static String accessToken;

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
        verifyThatAuthenticationIsDisabled();
    }

    @InSequence(1)
    @Test
    public void authenticate_notEncrypted_returns401() {
        final int alicePort = getAlicePort();
        given().
                port(alicePort).
                body(new AuthForm(validPassword)).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/user/authenticate").
//
        then().
                statusCode(401)
        ;
    }

    @InSequence(2)
    @Test
    public void changePassword_settingFirstPassword_enablesAuthentication() {
        final int alicePort = getAlicePort();
        accessToken = given().
                port(alicePort).
                body(new ChangePassword(validPassword, null)).
                contentType(ContentType.JSON).
//
        when().
                        post("/api/v1/user/password").
//
        then().
                        statusCode(200).
                        and().body("token", isA(String.class)).
                        extract().as(AuthResult.class).token;
        verifyThatAuthenticationIsEnabled();
        verifyThatAccessTokenIsValid(accessToken);
    }

    @InSequence(3)
    @Test
    public void changePassword_invalidOldPassword_returns401() {
        final int alicePort = getAlicePort();
        given().
                port(alicePort).
                body(new ChangePassword(invalidPassword, null)).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/user/password").
//
        then().
                statusCode(401)
        ;
        verifyThatAuthenticationIsEnabled();
        verifyThatAccessTokenIsValid(accessToken);
    }

    @InSequence(4)
    @Test
    public void authenticate_invalidCredentials_returns401() {
        final int alicePort = getAlicePort();
        given().
                port(alicePort).
                body(new AuthForm(invalidPassword)).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/user/authenticate").
//
        then().
                statusCode(401)
        ;
    }

    @InSequence(5)
    @Test
    public void authenticate_validCredentials_returnsAccessToken() {
        final String token = authenticate(validPassword);
        verifyThatAccessTokenIsValid(token);
        final String anotherToken = authenticate(validPassword);
        verifyThatAccessTokenIsValid(token);
        verifyThatAccessTokenIsValid(anotherToken);
    }

    @InSequence(6)
    @Test
    public void changePassword_settingAnotherPassword_enablesAuthentication() {
        final int alicePort = getAlicePort();
        final Faker faker = new Faker();
        final String oldPassword = UserResourceIT.validPassword;
        String newPassword;
        do {
            newPassword = faker.internet().password();
        } while (UserResourceIT.validPassword.equals(newPassword));
        UserResourceIT.validPassword = newPassword;
        UserResourceIT.invalidPassword = newPassword + newPassword;
        String oldAccessToken = accessToken;
        accessToken = given().
                port(alicePort).
                body(new ChangePassword(newPassword, oldPassword)).
                contentType(ContentType.JSON).
//
        when().
                        post("/api/v1/user/password").
//
        then().
                        statusCode(200).
                        and().body("token", isA(String.class)).
                        extract().as(AuthResult.class).token
        ;
        verifyThatAuthenticationIsEnabled();
        verifyThatAccessTokenIsInvalid(oldAccessToken);
        verifyThatAccessTokenIsValid(accessToken);
    }

    @InSequence(7)
    @Test
    public void changePassword_validOldPasswordAndNoNewPassword_removesAuthentication() {
        final int alicePort = getAlicePort();
        given().
                port(alicePort).
                body(new ChangePassword(null, validPassword)).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/user/password").
//
        then().
                statusCode(204)
        ;
        verifyThatAuthenticationIsDisabled();
    }

    private String authenticate(String password) {
        final int alicePort = getAlicePort();
        return given().
                port(alicePort).
                body(new AuthForm(password)).
                contentType(ContentType.JSON).
//
        when().
                        post("/api/v1/user/authenticate").
//
        then().
                        statusCode(200).
                        and().body("token", isA(String.class)).
                        extract().as(AuthResult.class).token;
    }

    private void verifyThatAccessTokenIsValid(String accessToken) {
        accessTokenVerificationRequest(accessToken).then().statusCode(200);
    }

    private void verifyThatAccessTokenIsInvalid(String accessToken) {
        accessTokenVerificationRequest(accessToken).then().statusCode(401);
    }

    private void verifyThatAuthenticationIsDisabled() {
        authenticationVerificationRequest().then().statusCode(200);
    }

    private void verifyThatAuthenticationIsEnabled() {
        authenticationVerificationRequest().then().statusCode(401);
    }

    private Response authenticationVerificationRequest() {
        final int alicePort = getAlicePort();
        return given().port(alicePort).when().get("/api/v1/offers");
    }

    private Response accessTokenVerificationRequest(String accessToken) {
        final int alicePort = getAlicePort();
        return given().port(alicePort).header("authorization", accessToken).when().get("/api/v1/offers");
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
