package bisq.api.http;

import bisq.api.http.model.AuthForm;
import bisq.api.http.model.AuthResult;
import bisq.api.http.model.ChangePassword;

import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;



import com.github.javafaker.Faker;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;

@RunWith(Arquillian.class)
public class UserEndpointIT {

    private static String validPassword = new Faker().internet().password();
    private static String invalidPassword = getRandomPasswordDifferentThan(validPassword);
    private static String accessToken;
    @DockerContainer
    Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, false);

    private static String getRandomPasswordDifferentThan(String otherPassword) {
        String newPassword;
        do {
            newPassword = new Faker().internet().password();
        } while (otherPassword.equals(newPassword));
        return newPassword;
    }

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
        verifyThatAuthenticationIsDisabled();
    }

    @InSequence(1)
    @Test
    public void authenticate_noPasswordSet_returns401() {
        int alicePort = getAlicePort();
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

    @InSequence(1)
    @Test
    public void authenticate_badJson_returns400() {
        int alicePort = getAlicePort();
        given().
                port(alicePort).
                body("{").
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/user/authenticate").
//
        then().
                statusCode(400)
        ;
    }

    @InSequence(2)
    @Test
    public void changePassword_settingFirstPassword_enablesAuthentication() {
        int alicePort = getAlicePort();
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
        int alicePort = getAlicePort();
        String newPassword = getRandomPasswordDifferentThan(validPassword);
        given().
                port(alicePort).
                body(new ChangePassword(newPassword, invalidPassword)).
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
        verifyThatPasswordIsValid(validPassword);
        verifyThatPasswordIsInvalid(newPassword);
    }

    @InSequence(3)
    @Test
    public void changePassword_emptyOldPassword_returns401() {
        int alicePort = getAlicePort();
        String newPassword = getRandomPasswordDifferentThan(validPassword);
        given().
                port(alicePort).
                body(new ChangePassword(newPassword, null)).
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
        verifyThatPasswordIsValid(validPassword);
        verifyThatPasswordIsInvalid(newPassword);
    }

    @InSequence(4)
    @Test
    public void authenticate_invalidCredentials_returns401() {
        int alicePort = getAlicePort();
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

    @InSequence(4)
    @Test
    public void authenticate_invalidCredentials_returnsNoAccessToken() {
        int alicePort = getAlicePort();
        String responseBody = given().
                port(alicePort).
                body(new AuthForm(invalidPassword)).
                accept(ContentType.JSON).
                contentType(ContentType.JSON).
//
        when().
                        post("/api/v1/user/authenticate").
//
        then().
                        extract().asString();
        assertEquals("", responseBody);
    }

    @InSequence(5)
    @Test
    public void authenticate_validCredentials_returnsAccessToken() {
        String token = authenticate(validPassword);
        verifyThatAccessTokenIsValid(token);
        String anotherToken = authenticate(validPassword);

        verifyThatAccessTokenIsValid(accessToken);
        verifyThatAccessTokenIsValid(token);
        verifyThatAccessTokenIsValid(anotherToken);
    }

    @InSequence(5)
    @Test
    public void authenticate_validCredentials_returnsDifferentAccessTokenEachTime() {
        String token = authenticate(validPassword);
        String anotherToken = authenticate(validPassword);

        assertNotEquals(accessToken, token);
        assertNotEquals(accessToken, anotherToken);
        assertNotEquals(token, anotherToken);
    }

    @InSequence(6)
    @Test
    public void changePassword_settingAnotherPassword_keepsAuthenticationEnabled() {
        int alicePort = getAlicePort();
        String oldPassword = validPassword;
        String newPassword = getRandomPasswordDifferentThan(validPassword);
        validPassword = newPassword;
        invalidPassword = getRandomPasswordDifferentThan(validPassword);
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
        verifyThatPasswordIsInvalid(oldPassword);
        verifyThatPasswordIsValid(newPassword);
        verifyThatAuthenticationIsEnabled();
        verifyThatAccessTokenIsInvalid(oldAccessToken);
        verifyThatAccessTokenIsValid(accessToken);
    }

    @InSequence(7)
    @Test
    public void changePassword_validOldPasswordAndNoNewPassword_disablesAuthentication() {
        int alicePort = getAlicePort();
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
        int alicePort = getAlicePort();
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

    private void verifyThatPasswordIsInvalid(String password) {
        int alicePort = getAlicePort();
        given().
                port(alicePort).
                body(new AuthForm(password)).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/user/authenticate").
//
        then().
                statusCode(401)
        ;
    }

    private void verifyThatPasswordIsValid(String password) {
        authenticate(password);
    }

    private Response authenticationVerificationRequest() {
        int alicePort = getAlicePort();
        return given().port(alicePort).when().get("/api/v1/version");
    }

    private Response accessTokenVerificationRequest(String accessToken) {
        int alicePort = getAlicePort();
        return given().port(alicePort).header("authorization", accessToken).when().get("/api/v1/version");
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
