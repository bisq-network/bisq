package bisq.httpapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bisq.httpapi.RegexMatcher.matchesRegex;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;



import bisq.httpapi.model.AuthForm;
import bisq.httpapi.model.AuthResult;
import bisq.httpapi.model.ChangePassword;
import bisq.httpapi.model.SeedWordsRestore;
import bisq.httpapi.model.WithdrawFundsForm;
import com.github.javafaker.Faker;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.arquillian.cube.spi.CubeOutput;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;

@RunWith(Arquillian.class)
public class WalletEndpointIT {

    @DockerContainer
    private Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, true);

    @DockerContainer
    private Container bitcoin = ContainerFactory.createBitcoinContainer();

    private static String password;
    private static String accessToken;
    private static String emptyMinerAddress;
    private static String addressWithFunds1;
    private static String addressWithFunds2;
    private static String addressWithFunds3;

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
    }

    @InSequence(1)
    @Test
    public void generateBitcoins() {
        ApiTestHelper.generateBlocks(bitcoin, 101);
        emptyMinerAddress = createNewAccountAndAddress();
    }

    @InSequence(1)
    @Test
    public void getOrCreateAvailableUnusedWalletAddresses_always_returns200() {
        given().
                port(getAlicePort()).
//
        when().
                post("/api/v1/wallet/addresses").
//
        then().
                statusCode(200).
                and().body("address", isA(String.class)).
                and().body("balance", isA(Number.class)).
                and().body("confirmations", isA(Number.class))
        ;
    }

    @InSequence(2)
    @Test
    public void withdrawFunds_insufficientFunds_returns423() throws InterruptedException {
        final int alicePort = getAlicePort();
        final String addressWithFunds1 = ApiTestHelper.getAvailableBtcWalletAddress(alicePort);

        final WithdrawFundsForm data = new WithdrawFundsForm();
        data.amount = 50000000;
        data.feeExcluded = false;
        data.sourceAddresses = Collections.singletonList(addressWithFunds1);
        data.targetAddress = emptyMinerAddress;

        given().
                port(alicePort).
                body(data).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/wallet/withdraw").
//
        then().
                statusCode(423);

        ApiTestHelper.waitForP2PMsgPropagation();
        assertEquals(0, ApiTestHelper.getBalance(alicePort).availableBalance);
        ApiTestHelper.generateBlocks(bitcoin, 1);
        assertEquals(0, getAccountBalanceByAddress(bitcoin, emptyMinerAddress), 0);
    }

    @InSequence(3)
    @Test
    public void fundAliceWallet() throws InterruptedException {
        final int alicePort = getAlicePort();
        addressWithFunds1 = ApiTestHelper.getAvailableBtcWalletAddress(alicePort);
        ApiTestHelper.sendFunds(bitcoin, addressWithFunds1, .3);
        ApiTestHelper.generateBlocks(bitcoin, 1);
        addressWithFunds2 = ApiTestHelper.getAvailableBtcWalletAddress(alicePort);
        ApiTestHelper.sendFunds(bitcoin, addressWithFunds2, .3);
        ApiTestHelper.generateBlocks(bitcoin, 1);
        addressWithFunds3 = ApiTestHelper.getAvailableBtcWalletAddress(alicePort);
        ApiTestHelper.sendFunds(bitcoin, addressWithFunds3, .4);
        ApiTestHelper.generateBlocks(bitcoin, 1);
        ApiTestHelper.waitForP2PMsgPropagation();
    }

    @InSequence(4)
    @Test
    public void withdrawFunds_dust_returns424() throws InterruptedException {
        final int alicePort = getAlicePort();
        final String addressWithFunds1 = ApiTestHelper.getAvailableBtcWalletAddress(alicePort);

        final WithdrawFundsForm data = new WithdrawFundsForm();
        data.amount = 1;
        data.feeExcluded = false;
        data.sourceAddresses = Arrays.asList(addressWithFunds1, addressWithFunds2, addressWithFunds3);
        data.targetAddress = emptyMinerAddress;

        given().
                port(alicePort).
                body(data).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/wallet/withdraw").
//
        then().
                statusCode(424);

        ApiTestHelper.waitForP2PMsgPropagation();
        assertEquals(100000000, ApiTestHelper.getBalance(alicePort).availableBalance);
        ApiTestHelper.generateBlocks(bitcoin, 1);
        assertEquals(0, getAccountBalanceByAddress(bitcoin, emptyMinerAddress), 0);
    }

    @InSequence(5)
    @Test
    public void withdrawFunds_insufficientFundsDueToFee_returns423() throws InterruptedException {
        final int alicePort = getAlicePort();

        final WithdrawFundsForm data = new WithdrawFundsForm();
        data.amount = 100000000;
        data.feeExcluded = true;
        data.sourceAddresses = Arrays.asList(addressWithFunds1, addressWithFunds2, addressWithFunds3);
        data.targetAddress = emptyMinerAddress;

        given().
                port(alicePort).
                body(data).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/wallet/withdraw").
//
        then().
                statusCode(423);

        ApiTestHelper.waitForP2PMsgPropagation();
        assertEquals(100000000, ApiTestHelper.getBalance(alicePort).availableBalance);
        ApiTestHelper.generateBlocks(bitcoin, 1);
        assertEquals(0, getAccountBalanceByAddress(bitcoin, emptyMinerAddress), 0);
    }

    @InSequence(6)
    @Test
    public void withdrawFunds_sufficientFunds_returns204() throws InterruptedException {
        final int alicePort = getAlicePort();

        final WithdrawFundsForm data = new WithdrawFundsForm();
        data.amount = 100000000;
        data.feeExcluded = false;
        data.sourceAddresses = Arrays.asList(addressWithFunds1, addressWithFunds2, addressWithFunds3);
        data.targetAddress = emptyMinerAddress;

        given().
                port(alicePort).
                body(data).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/wallet/withdraw").
//
        then().
                statusCode(204);

        ApiTestHelper.waitForP2PMsgPropagation();
        assertEquals(0, ApiTestHelper.getBalance(alicePort).availableBalance);
        ApiTestHelper.generateBlocks(bitcoin, 1);
        assertEquals(1d, getAccountBalanceByAddress(bitcoin, emptyMinerAddress), .01);
    }

    @InSequence(7)
    @Test
    public void withdrawFunds_sufficientFundsExcludingFee_returns204() throws InterruptedException {
        final int alicePort = getAlicePort();

        emptyMinerAddress = createNewAccountAndAddress();
        fundAliceWallet();

        final WithdrawFundsForm data = new WithdrawFundsForm();
        data.amount = 50000000;
        data.feeExcluded = true;
        data.sourceAddresses = Arrays.asList(addressWithFunds1, addressWithFunds2, addressWithFunds3);
        data.targetAddress = emptyMinerAddress;

        given().
                port(alicePort).
                body(data).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/wallet/withdraw").
//
        then().
                statusCode(204);

        ApiTestHelper.waitForP2PMsgPropagation();
        assertEquals(50000000, ApiTestHelper.getBalance(alicePort).availableBalance);
        ApiTestHelper.generateBlocks(bitcoin, 1);
        assertEquals(.5, getAccountBalanceByAddress(bitcoin, emptyMinerAddress), .01);
    }

    @InSequence(8)
    @Test
    public void getSeedWords_noPasswordSetAndNoPasswordProvided_returns200() throws Exception {
        given().
                port(getAlicePort()).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/wallet/seed-words/retrieve").
//
        then().
                statusCode(200).
                and().body("mnemonicCode", isA(List.class)).
                and().body("mnemonicCode[0]", isA(String.class)).
                and().body("walletCreationDate", isA(String.class)).
                and().body("walletCreationDate", matchesRegex("\\d\\d\\d\\d-\\d\\d-\\d\\d"))
        ;
    }

    @InSequence(9)
    @Test
    public void getSeedWords_noPasswordSetAndPasswordProvided_returns200() throws Exception {
        given().
                port(getAlicePort()).
                contentType(ContentType.JSON).
                body(new AuthForm(password)).
//
        when().
                post("/api/v1/wallet/seed-words/retrieve").
//
        then().
                statusCode(200).
                and().body("mnemonicCode", isA(List.class)).
                and().body("mnemonicCode[0]", isA(String.class)).
                and().body("walletCreationDate", isA(String.class)).
                and().body("walletCreationDate", matchesRegex("\\d\\d\\d\\d-\\d\\d-\\d\\d"))
        ;
    }

    @InSequence(10)
    @Test
    public void setPassword() throws Exception {
        final int alicePort = getAlicePort();
        password = new Faker().internet().password();
        accessToken = given().
                port(alicePort).
                body(new ChangePassword(password, null)).
                contentType(ContentType.JSON).
//
        when().
                        post("/api/v1/user/password").
//
        then().
                        statusCode(200).
                        and().body("token", isA(String.class)).
                        extract().as(AuthResult.class).token;
    }

    @InSequence(11)
    @Test
    public void restoreWalletFromSeedWords_passwordSetAndAccessTokenNotProvided_returns401() throws Exception {
        given().
                port(getAlicePort()).
                contentType(ContentType.JSON).
                body(new SeedWordsRestore(Collections.emptyList(), "2018-04-28", password)).
//
        when().
                post("/api/v1/wallet/seed-words/restore").
//
        then().
                statusCode(401)
        ;
    }

    @InSequence(11)
    @Test
    public void restoreWalletFromSeedWords_passwordSetAndPasswordNotProvided_returns401() throws Exception {
        final SeedWordsRestore payload = new SeedWordsRestore(Collections.singletonList("string"), "2018-04-28");
        restoreWalletFromSeedWords_request(payload, 401, true);
    }

    @InSequence(11)
    @Test
    public void restoreWalletFromSeedWords_noContent_returns422() throws Exception {
        restoreWalletFromSeedWords_request(null, 422, true);
    }

    @InSequence(11)
    @Test
    public void restoreWalletFromSeedWords_noMnemonicCode_returns422() throws Exception {
        final SeedWordsRestore payload = new SeedWordsRestore(null, "2018-04-28", password);
        restoreWalletFromSeedWords_request(payload, 422, true);
    }

    @InSequence(11)
    @Test
    public void restoreWalletFromSeedWords_mnemonicCodeContainsNull_returns422() throws Exception {
        final SeedWordsRestore payload = new SeedWordsRestore(Collections.singletonList(null), "2018-04-28", password);
        restoreWalletFromSeedWords_request(payload, 422, true);
    }

    @InSequence(11)
    @Test
    public void restoreWalletFromSeedWords_emptyMnemonicCodeArray_returns422() throws Exception {
        final SeedWordsRestore payload = new SeedWordsRestore(Collections.emptyList(), "abcd", password);
        restoreWalletFromSeedWords_request(payload, 422, true);
    }

    @InSequence(11)
    @Test
    public void restoreWalletFromSeedWords_walletCreationDateIsNull_returns422() throws Exception {
        final SeedWordsRestore payload = new SeedWordsRestore(Collections.emptyList(), null, password);
        restoreWalletFromSeedWords_request(payload, 422, true);
    }

    @InSequence(11)
    @Test
    public void restoreWalletFromSeedWords_doesNotMatchDatePattern_returns422() throws Exception {
        final SeedWordsRestore payload = new SeedWordsRestore(Collections.emptyList(), "abcd", password);
        restoreWalletFromSeedWords_request(payload, 422, true);
    }

    @InSequence(12)
    @Test
    public void restoreWalletFromSeedWords_correctData_returns204() throws Exception {
        final SeedWordsRestore payload = new SeedWordsRestore(Collections.singletonList("string"), "2018-01-01", password);
        restoreWalletFromSeedWords_request(payload, 204, true);
    }

    private double getAccountBalanceByAddress(Container bitcoin, String address) {
        CubeOutput cubeOutput = bitcoin.exec("bitcoin-cli", "-regtest", "getaccount", address);
        assertEquals("Command 'getnewaddress' should succeed", "", cubeOutput.getError());
        final String account = cubeOutput.getStandard().trim();
        cubeOutput = bitcoin.exec("bitcoin-cli", "-regtest", "getbalance", account);
        assertEquals("Command 'getbalance' should succeed", "", cubeOutput.getError());
        return Double.valueOf(cubeOutput.getStandard().trim());
    }

    private String createNewAccountAndAddress() {
        final CubeOutput cubeOutput = bitcoin.exec("bitcoin-cli", "-regtest", "getnewaddress", "test" + System.currentTimeMillis());
        assertEquals("Command 'getnewaddress' should succeed", "", cubeOutput.getError());
        return cubeOutput.getStandard().trim();
    }

    private void restoreWalletFromSeedWords_request(SeedWordsRestore payload, int expectedStatusCode, boolean includeAccessTokenHeader) {
        final RequestSpecification requestSpecification = given().
                port(getAlicePort()).
                contentType(ContentType.JSON);

        if (null != payload)
            requestSpecification.body(payload);

        if (includeAccessTokenHeader)
            requestSpecification.header("authorization", accessToken);

        requestSpecification.
//
        when().
                post("/api/v1/wallet/seed-words/restore").
//
        then().
                statusCode(expectedStatusCode)
        ;
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
