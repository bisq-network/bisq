package io.bisq.api;

import io.bisq.api.model.payment.SepaPaymentAccount;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.Trade;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.hamcrest.Matcher;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

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

    @InSequence(1)
    @Test
    public void getTrades_returnsTrade() {
        final int alicePort = getAlicePort();

        final SepaPaymentAccount alicePaymentAccount = OfferResourceIT.alicePaymentAccount;
        final SepaPaymentAccount bobPaymentAccount = OfferResourceIT.bobPaymentAccount;

        given().
                port(getBobPort()).
//
        when().
                get("/api/v1/trades").
//
        then().
                statusCode(200)
                .and().body("trades[0].id", isA(String.class)).

                and().body("trades[0].offer.id", isA(String.class)).
                and().body("trades[0].offer.acceptedCountryCodes", equalTo(alicePaymentAccount.acceptedCountries)).
                and().body("trades[0].offer.amount", equalTo(6250000)).
                and().body("trades[0].offer.arbitratorNodeAddresses", equalTo(ApiTestHelper.getAcceptedArbitrators(alicePort))).
                and().body("trades[0].offer.baseCurrencyCode", equalTo("BTC")).
                and().body("trades[0].offer.bankId", equalTo(alicePaymentAccount.bic)).
                and().body("trades[0].offer.blockHeightAtOfferCreation", isA(Integer.class)).
                and().body("trades[0].offer.buyerSecurityDeposit", equalTo((int) OfferResourceIT.createdOffer.buyerSecurityDeposit)).
                and().body("trades[0].offer.counterCurrencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("trades[0].offer.countryCode", equalTo(alicePaymentAccount.countryCode)).
                and().body("trades[0].offer.currencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("trades[0].offer.date", isA(Long.class)).
                and().body("trades[0].offer.direction", equalTo(OfferPayload.Direction.BUY.name())).
                and().body("trades[0].offer.id", isA(String.class)).
                and().body("trades[0].offer.isCurrencyForMakerFeeBtc", equalTo(true)).
                and().body("trades[0].offer.isPrivateOffer", equalTo(false)).
                and().body("trades[0].offer.lowerClosePrice", equalTo(0)).
                and().body("trades[0].offer.makerFee", equalTo(12500)).
                and().body("trades[0].offer.makerPaymentAccountId", equalTo(alicePaymentAccount.id)).
                and().body("trades[0].offer.marketPriceMargin", equalTo(0f)).
                and().body("trades[0].offer.maxTradeLimit", equalTo(25000000)).
                and().body("trades[0].offer.maxTradePeriod", equalTo(518400000)).
                and().body("trades[0].offer.minAmount", equalTo(6250000)).
                and().body("trades[0].offer.offerFeePaymentTxId", isA(String.class)).
                and().body("trades[0].offer.ownerNodeAddress", equalTo(ApiTestHelper.getP2PNetworkStatus(alicePort).address)).
                and().body("trades[0].offer.paymentMethodId", equalTo(alicePaymentAccount.paymentMethod)).
                and().body("trades[0].offer.price", equalTo(10)).
                and().body("trades[0].offer.protocolVersion", equalTo(1)).
                and().body("trades[0].offer.sellerSecurityDeposit", equalTo(300000)).
                and().body("trades[0].offer.state", equalTo(Offer.State.AVAILABLE.name())).
                and().body("trades[0].offer.txFee", isA(Integer.class)).
                and().body("trades[0].offer.upperClosePrice", equalTo(0)).
                and().body("trades[0].offer.useAutoClose", equalTo(false)).
                and().body("trades[0].offer.useMarketBasedPrice", equalTo(false)).
                and().body("trades[0].offer.useReOpenAfterAutoClose", equalTo(false)).
                and().body("trades[0].offer.versionNr", isA(String.class)).

                and().body("trades[0].buyerPaymentAccount.paymentMethod", equalTo(alicePaymentAccount.paymentMethod)).
                and().body("trades[0].buyerPaymentAccount.paymentDetails", isA(String.class)).
                and().body("trades[0].buyerPaymentAccount.countryCode", equalTo(alicePaymentAccount.countryCode)).
                and().body("trades[0].buyerPaymentAccount.holderName", equalTo(alicePaymentAccount.holderName)).
                and().body("trades[0].buyerPaymentAccount.bic", equalTo(alicePaymentAccount.bic)).
                and().body("trades[0].buyerPaymentAccount.iban", equalTo(alicePaymentAccount.iban)).
                and().body("trades[0].buyerPaymentAccount.acceptedCountries", equalTo(alicePaymentAccount.acceptedCountries)).

                and().body("trades[0].sellerPaymentAccount.paymentMethod", equalTo(bobPaymentAccount.paymentMethod)).
                and().body("trades[0].sellerPaymentAccount.paymentDetails", isA(String.class)).
                and().body("trades[0].sellerPaymentAccount.countryCode", equalTo(bobPaymentAccount.countryCode)).
                and().body("trades[0].sellerPaymentAccount.holderName", equalTo(bobPaymentAccount.holderName)).
                and().body("trades[0].sellerPaymentAccount.bic", equalTo(bobPaymentAccount.bic)).
                and().body("trades[0].sellerPaymentAccount.iban", equalTo(bobPaymentAccount.iban)).
                and().body("trades[0].sellerPaymentAccount.acceptedCountries", equalTo(bobPaymentAccount.acceptedCountries)).

                and().body("trades[0].isCurrencyForTakerFeeBtc", equalTo(true)).
                and().body("trades[0].txFee", isA(Integer.class)).
                and().body("trades[0].takerFee", isA(Integer.class)).
                and().body("trades[0].takeOfferDate", isA(Long.class)).
                and().body("trades[0].takerFeeTxId", isA(String.class)).
                and().body("trades[0].payoutTxId", isEmptyOrNullString()).
                and().body("trades[0].tradeAmount", equalTo(6250000)).
                and().body("trades[0].tradePrice", equalTo(10)).
                and().body("trades[0].state", isOneOf(ApiTestHelper.toString(Trade.State.values()))).
                and().body("trades[0].disputeState", equalTo(Trade.DisputeState.NO_DISPUTE.name())).
                and().body("trades[0].tradePeriodState", equalTo(Trade.TradePeriodState.FIRST_HALF.name())).
                and().body("trades[0].arbitratorBtcPubKey", isA(String.class)).
                and().body("trades[0].contractHash", isA(String.class)).
                and().body("trades[0].mediatorNodeAddress", isA(String.class)).
                and().body("trades[0].takerContractSignature", isA(String.class)).
                and().body("trades[0].makerContractSignature", isEmptyOrNullString()).
                and().body("trades[0].arbitratorNodeAddress", isA(String.class)).
                and().body("trades[0].tradingPeerNodeAddress", isA(String.class)).
                and().body("trades[0].takerPaymentAccountId", equalTo(bobPaymentAccount.id)).
                and().body("trades[0].counterCurrencyTxId", isEmptyOrNullString())
        ;
    }


    @Ignore
    @InSequence(2)
    @Test
    public void paymentStarted_invokedBySeller_returnsXXX() {

    }

    @InSequence(2)
    @Test
    public void paymentStarted_missingId_returns404() {
        paymentStarted_template("", 404);
    }


    @InSequence(2)
    @Test
    public void paymentStarted_tradeDoesNotExist_returns404() {
        paymentStarted_template(tradeId + "1", 404);
    }


    @InSequence(2)
    @Test
    public void paymentStarted_beforeBlockGenerated_returns422() {
        paymentStarted_template(tradeId, 422);
    }

    @InSequence(3)
    @Test
    public void generateBitcoinBlock() {
        ApiTestHelper.generateBlocks(bitcoin, 1);
    }


    @InSequence(4)
    @Test
    public void paymentReceived_beforePaymentStarted_returns422() {
        paymentReceived_template(tradeId, 422);
    }

    @InSequence(5)
    @Test
    public void paymentStarted_tradeExists_returns200() {
        paymentStarted_template(tradeId, 200);
        assertTradeState(tradeId, Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
    }

    @InSequence(5)
    @Test
    public void paymentReceived_tradeDoesNotExist_returns404() {
        paymentReceived_template(tradeId + "1", 404);
    }

    @InSequence(6)
    @Test
    public void moveFundsToBisqWallet_beforeTradeComplete_returns422() {
        final String unknownTradeId = tradeId;
        moveFundsToBisqWallet_template(getAlicePort(), unknownTradeId, 422);
    }

    @InSequence(7)
    @Test
    public void paymentReceived_tradeExists_returns200() throws Exception {
        paymentReceived_template(tradeId, 200);
        ApiTestHelper.waitForP2PMsgPropagation();
        assertTradeState(tradeId, Trade.State.BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG);
    }

    @InSequence(8)
    @Test
    public void moveFundsToBisqWallet_tradeNotFound_returns404() {
        final String unknownTradeId = tradeId + tradeId;
        moveFundsToBisqWallet_template(getAlicePort(), unknownTradeId, 404);
        assertTradeNotFound(getAlicePort(), unknownTradeId);
    }

    @InSequence(8)
    @Test
    public void moveFundsToBisqWallet_tradeExists_returns200() {
        moveFundsToBisqWallet_template(getAlicePort(), tradeId, 204);
        moveFundsToBisqWallet_template(getBobPort(), tradeId, 204);
        assertTradeNotFound(getBobPort(), tradeId);
        assertTradeNotFound(getAlicePort(), tradeId);
        assertWalletBalance(getAlicePort(), greaterThan(100000000));
        assertWalletBalance(getBobPort(), lessThan((int) (100000000 - OfferResourceIT.createdOffer.amount)));
    }

    private void assertWalletBalance(int apiPort, Matcher matcher) {
        given().
                port(apiPort).
//
        when().
                get("/api/v1/wallet").
//
        then().
                statusCode(200).
                and().body("availableBalance", matcher)
        ;
    }

    private void assertTradeNotFound(int apiPort, String tradeId) {
        given().
                port(apiPort).
//
        when().
                get("/api/v1/trades/" + tradeId).
//
        then().
                statusCode(404);
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

    private void moveFundsToBisqWallet_template(int apiPort, String tradeId, int expectedStatusCode) {
        given().
                port(apiPort).
//
        when().
                post("/api/v1/trades/" + tradeId + "/move-funds-to-bisq-wallet").
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
