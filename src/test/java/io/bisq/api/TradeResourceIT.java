package io.bisq.api;

import io.bisq.core.trade.Trade;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@RunWith(Arquillian.class)
public class TradeResourceIT {

    @DockerContainer
    Container alice;

    @DockerContainer
    Container bob;

    @DockerContainer
    Container arbitrator;

    @SuppressWarnings("unused")
    @DockerContainer(order = 4)
    Container seedNode;

    @DockerContainer
    Container bitcoin;

    OfferResourceIT offerResourceIT = new OfferResourceIT();

    private static String tradeId;

    {
        alice = offerResourceIT.alice;
        bob = offerResourceIT.bob;
        arbitrator = offerResourceIT.arbitrator;
        seedNode = offerResourceIT.seedNode;
        bitcoin = offerResourceIT.bitcoin;
    }

    @InSequence
    @Test
    public void setupTrade() throws Exception {
        final OfferResourceIT offerResourceIT = new OfferResourceIT();
        offerResourceIT.alice = alice;
        offerResourceIT.bob = bob;
        offerResourceIT.arbitrator = arbitrator;
        offerResourceIT.seedNode = seedNode;
        offerResourceIT.bitcoin = bitcoin;
        offerResourceIT.waitForAllServicesToBeReady();
        offerResourceIT.registerArbitrator();
        offerResourceIT.fundAliceWallet();
        offerResourceIT.createOffer_validPayloadAndHasFunds_returnsOffer();
        ApiTestHelper.waitForP2PMsgPropagation();
        offerResourceIT.selectSameArbitratorAsInOffer();
        offerResourceIT.fundBobWallet();
        offerResourceIT.takeOffer_validPaymentMethodAndHasFunds_returnsTrade();
        tradeId = given().
                port(getAlicePort()).
//
        when().
                        get("/api/v1/trades").
//
        then().
                        statusCode(200)
                .extract().jsonPath().getString("trades[0].id");
    }

    @Ignore
    @InSequence(1)
    @Test
    public void paymentStarted_invokedBySeller_returnsXXX() throws Exception {

    }

    @InSequence(1)
    @Test
    public void paymentStarted_missingId_returns404() throws Exception {
        paymentStarted_template("", 404);
    }


    @InSequence(1)
    @Test
    public void paymentStarted_tradeDoesNotExist_returns404() throws Exception {
        paymentStarted_template(tradeId + "1", 404);
    }


    @InSequence(1)
    @Test
    public void paymentStarted_beforeBlockGenerated_returns422() throws Exception {
        paymentStarted_template(tradeId, 422);
    }

    @InSequence(2)
    @Test
    public void generateBitcoinBlock() {
        ApiTestHelper.generateBlocks(bitcoin, 1);
    }


    @InSequence(3)
    @Test
    public void paymentReceived_beforePaymentStarted_returns422() throws Exception {
        paymentReceived_template(tradeId, 422);
    }

    @InSequence(4)
    @Test
    public void paymentStarted_tradeExists_returns200() throws Exception {
        paymentStarted_template(tradeId, 200);
        assertTradeState(tradeId, Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
    }

    @InSequence(4)
    @Test
    public void paymentReceived_tradeDoesNotExist_returns404() throws Exception {
        paymentReceived_template(tradeId + "1", 404);
    }

    @InSequence(5)
    @Test
    public void paymentReceived_tradeExists_returns200() throws Exception {
        paymentReceived_template(tradeId, 200);
        ApiTestHelper.waitForP2PMsgPropagation();
        assertTradeState(tradeId, Trade.State.BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG);
    }

    private void assertTradeState(String tradeId, Trade.State state) {
        given().
                port(getAlicePort()).
//
        when().
                get("/api/v1/trades/" + tradeId).
//
        then().
                statusCode(200).
                and().body("state", equalTo(state.name()));
    }

    private void paymentStarted_template(String tradeId, int expectedStatusCode) {
        given().
                port(getAlicePort()).
//
        when().
                post("/api/v1/trades/" + tradeId + "/payment-started").
//
        then().
                statusCode(expectedStatusCode)
        ;
    }

    private void paymentReceived_template(String tradeId, int expectedStatusCode) {
        given().
                port(getBobPort()).
//
        when().
                post("/api/v1/trades/" + tradeId + "/payment-received").
//
        then().
                statusCode(expectedStatusCode)
        ;
    }

    private int getBobPort() {
        return bob.getBindPort(8080);
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }
}
