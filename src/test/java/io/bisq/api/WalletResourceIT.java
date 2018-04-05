package io.bisq.api;

import io.bisq.api.service.v1.WithdrawFundsForm;
import io.restassured.http.ContentType;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.arquillian.cube.spi.CubeOutput;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;

@RunWith(Arquillian.class)
public class WalletResourceIT {

    @DockerContainer
    private Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, true);

    @DockerContainer
    private Container bitcoin = ContainerFactory.createBitcoinContainer();

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

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
