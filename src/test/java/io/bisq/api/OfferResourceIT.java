package io.bisq.api;

import io.bisq.api.model.*;
import io.bisq.api.model.payment.SepaPaymentAccount;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.trade.Trade;
import io.restassured.http.ContentType;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@RunWith(Arquillian.class)
public class OfferResourceIT {

    @DockerContainer
    Container alice = ContainerFactory.createApiContainer("alice", "8080->8080", 3333, true, true);

    @DockerContainer
    Container bob = ContainerFactory.createApiContainer("bob", "8081->8080", 3333, true, true);

    @DockerContainer
    Container arbitrator = ContainerFactory.createApiContainer("arbitrator", "8082->8080", 3335, true, true);

    @SuppressWarnings("unused")
    @DockerContainer(order = 4)
    Container seedNode = ContainerFactory.createSeedNodeContainer();

    @DockerContainer
    Container bitcoin = ContainerFactory.createBitcoinContainer();

    private static String alicePaymentAccountId;
    private static String bobPaymentAccountId;
    private static String bobIncompatiblePaymentAccountId;
    private static String tradeCurrency;
    private static String createdOfferId;

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws Exception {
        ApiTestHelper.waitForAllServicesToBeReady();
        addPaymentAccounts();
    }

    private void addPaymentAccounts() {
        final int alicePort = getAlicePort();
        final int bobPort = getBobPort();

        SepaPaymentAccount sepaAccountToCreate;

        sepaAccountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload();
        tradeCurrency = sepaAccountToCreate.selectedTradeCurrency;
        alicePaymentAccountId = ApiTestHelper.createPaymentAccount(alicePort, sepaAccountToCreate).extract().body().jsonPath().get("id");

        sepaAccountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload(tradeCurrency);
        bobPaymentAccountId = ApiTestHelper.createPaymentAccount(bobPort, sepaAccountToCreate).extract().body().jsonPath().get("id");

        final String incompatibleCurrency = "EUR".equals(tradeCurrency) ? "PLN" : "EUR";
        sepaAccountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload(incompatibleCurrency);
        bobIncompatiblePaymentAccountId = ApiTestHelper.createPaymentAccount(bobPort, sepaAccountToCreate).extract().body().jsonPath().get("id");
    }

    @InSequence(1)
    @Test
    public void createOffer_noArbitratorAccepted_returns424status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccountId);
        createOffer_template(offer, 424);
    }

    private void createOffer_template(OfferToCreate offer, int expectedStatusCode) {
        given().
                port(getAlicePort()).
                body(offer).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/offers").
//
        then().
                statusCode(expectedStatusCode)
        ;
    }

    @InSequence(2)
    @Test
    public void registerArbitrator() throws Exception {
        ApiTestHelper.registerArbitrator(getArbitratorPort());
    }

    @InSequence(3)
    @Test
    public void createOffer_validPayloadButNoFunds_returns427status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccountId);
        createOffer_template(offer, 427);
    }

    @InSequence(3)
    @Test
    public void createOffer_incompatiblePaymentAccount_returns423status() {
        String otherTradeCurrency = "EUR".equals(tradeCurrency) ? "PLN" : "EUR";
        final OfferToCreate offer = getOfferToCreateFixedBuy(otherTradeCurrency, alicePaymentAccountId);
        createOffer_template(offer, 423);
    }

    @InSequence(3)
    @Test
    public void createOffer_noPaymentAccount_returns425status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccountId + alicePaymentAccountId);
        createOffer_template(offer, 425);
    }

    @InSequence(3)
    @Test
    public void createOffer_useMarketBasePriceButNoMarginProvided_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccountId);
        offer.priceType = PriceType.PERCENTAGE;
        offer.percentageFromMarketPrice = null;
        createOffer_template(offer, 422);
    }

    @InSequence(3)
    @Test
    public void createOffer_notUseMarketBasePriceButNoFixedPrice_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccountId);
        offer.priceType = PriceType.FIXED;
        final JSONObject jsonOffer = toJsonObject(offer);
        jsonOffer.remove("fixedPrice");
        given().
                port(getAlicePort()).
                body(jsonOffer.toString()).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/offers").
//
        then().
                statusCode(422)
        ;
    }

    @InSequence(4)
    @Test
    public void fundAliceWallet() {
        ApiTestHelper.generateBlocks(bitcoin, 101);
        String walletAddress = ApiTestHelper.getAvailableBtcWalletAddress(getAlicePort());
        ApiTestHelper.sendFunds(bitcoin, walletAddress, 1);
        ApiTestHelper.generateBlocks(bitcoin, 1);
    }

    @InSequence(5)
    @Test
    public void createOffer_validPayloadAndHasFunds_returnsOffer() throws Exception {
        final int alicePort = getAlicePort();

        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccountId);

        createdOfferId = given().
                port(alicePort).
                body(offer).
                contentType(ContentType.JSON).
//
        when().
                        post("/api/v1/offers").
//
        then().
                        statusCode(200).
                        and().body("offer_id", isA(String.class)).
                        and().body("created", isA(Long.class)).
                        and().body("arbitrators", equalTo(ApiTestHelper.getAcceptedArbitrators(alicePort))).
                        and().body("offerer", equalTo(ApiTestHelper.getP2PNetworkStatus(alicePort).address)).
//                TODO shortly after offer is created it changes state to UNKNOWN
        and().body("state", equalTo(Offer.State.OFFER_FEE_PAID.name())).
                        and().body("btc_amount", equalTo("0.00000001")).
                        and().body("min_btc_amount", equalTo("0.00000001")).
                        and().body("other_amount", equalTo("0.001")).
                        and().body("other_currency", equalTo(tradeCurrency)).
                        and().body("price_detail.use_market_price", equalTo(false)).
                        and().body("price_detail.market_price_margin", equalTo(offer.percentageFromMarketPrice.floatValue())).
                        and().body("direction", equalTo(offer.direction.name())).
                        extract().jsonPath().getString("offer_id");
    }

    @InSequence(6)
    @Test
    public void listOffers_always_returnsOffers() throws InterruptedException {
        final int alicePort = getAlicePort();

        given().
                port(alicePort).
//
        when().
                get("/api/v1/offers").
//
        then().
                statusCode(200).
                and().body("total", equalTo(1)).
                and().body("offers.size()", equalTo(1)).
                and().body("offers[0].offer_id", isA(String.class)).
                and().body("offers[0].created", isA(Long.class)).
                and().body("offers[0].arbitrators.size()", equalTo(1)).
                and().body("offers[0].offerer", isA(String.class)).
                and().body("offers[0].state", isA(String.class)).
                and().body("offers[0].btc_amount", equalTo("0.00000001")).
                and().body("offers[0].min_btc_amount", equalTo("0.00000001")).
                and().body("offers[0].other_amount", equalTo("0.001")).
                and().body("offers[0].other_currency", isA(String.class)).
                and().body("offers[0].price_detail.use_market_price", equalTo(false)).
                and().body("offers[0].price_detail.market_price_margin", isA(Float.class)).
                and().body("offers[0].direction", equalTo(OfferPayload.Direction.BUY.name()))
        ;
    }

    @InSequence(7)
    @Test
    public void takeOffer_offerNotFound_returns404status() throws Exception {
        final TakeOffer payload = new TakeOffer(bobPaymentAccountId, "1");
        takeOffer_template("non-existing-id", payload, 404);
    }


    private void takeOffer_template(String offerId, TakeOffer payload, int expectedStatusCode) {
        given().
                port(getBobPort()).
                body(payload).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/offers/" + offerId + "/take").
//
        then().
                statusCode(expectedStatusCode)
        ;
    }

    @InSequence(7)
    @Test
    public void takeOffer_validPayloadButNoFunds_returns427status() throws Exception {
        final TakeOffer payload = new TakeOffer(bobPaymentAccountId, "1");
        takeOffer_template(createdOfferId, payload, 427);
    }

    @InSequence(7)
    @Test
    public void takeOffer_paymentAccountIdMissing_returns422status() throws Exception {
        final TakeOffer payload = new TakeOffer(null, "1");
        takeOffer_template(createdOfferId, payload, 422);
    }

    @InSequence(7)
    @Test
    public void takeOffer_amountMissing_returns422() throws Exception {
        final TakeOffer payload = new TakeOffer(bobPaymentAccountId, null);
        takeOffer_template(createdOfferId, payload, 422);
    }

    @InSequence(7)
    @Test
    public void takeOffer_paymentAccountNotFound_returns425() throws Exception {
        final TakeOffer payload = new TakeOffer("non-existing-account", "1");
        takeOffer_template(createdOfferId, payload, 425);
    }

    @InSequence(7)
    @Test
    public void takeOffer_incompatiblePaymentAccount_returns423() throws Exception {
        final TakeOffer payload = new TakeOffer(bobIncompatiblePaymentAccountId, "1");
        takeOffer_template(createdOfferId, payload, 423);
    }

    @Ignore("Bug in tradeManager.onTakeOffer which resolves instead of reject in this scenario")
    @InSequence(7)
    @Test
    public void takeOffer_noArbitratorSelected_returns424() throws Exception {
        ApiTestHelper.deselectAllArbitrators(getBobPort());
        final TakeOffer payload = new TakeOffer(bobPaymentAccountId, "1");
        takeOffer_template(createdOfferId, payload, 423);
    }

    @Ignore("Bug in tradeManager.onTakeOffer which resolves instead of reject in this scenario")
    @InSequence(7)
    @Test
    public void takeOffer_noOverlappingArbitrator_returnsXXX() throws Exception {
        final int bobPort = getBobPort();
        ApiTestHelper.registerArbitrator(getAlicePort());
        final OfferDetail offer = ApiTestHelper.getOfferById(bobPort, createdOfferId);
        final List<String> arbitrators = ApiTestHelper.getAcceptedArbitrators(bobPort);
        arbitrators.removeAll(offer.arbitrators);
        Assert.assertThat(arbitrators.size(), greaterThan(0));
        ApiTestHelper.deselectAllArbitrators(bobPort);
        ApiTestHelper.selectArbitrator(bobPort, arbitrators.get(0));

        final TakeOffer payload = new TakeOffer(bobPaymentAccountId, "1");
        takeOffer_template(createdOfferId, payload, 0);
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @InSequence(8)
    @Test
    public void selectSameArbitratorAsInOffer() throws Exception {
        final int bobPort = getBobPort();
        final OfferDetail offer = ApiTestHelper.getOfferById(bobPort, createdOfferId);
        Assert.assertThat(offer.arbitrators.size(), greaterThan(0));
        ApiTestHelper.selectArbitrator(bobPort, offer.arbitrators.get(0));
    }

    @InSequence(9)
    @Test
    public void fundBobWallet() {
        ApiTestHelper.generateBlocks(bitcoin, 101);
        String walletAddress = ApiTestHelper.getAvailableBtcWalletAddress(getBobPort());
        ApiTestHelper.sendFunds(bitcoin, walletAddress, 1);
        ApiTestHelper.generateBlocks(bitcoin, 1);
    }

    @InSequence(10)
    @Test
    public void takeOffer_validPaymentMethodAndHasFunds_returnsTrade() throws Exception {
        final int alicePort = getAlicePort();
        final int bobPort = getBobPort();

        final SepaPaymentAccount sepaAccountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload(tradeCurrency);
        sepaAccountToCreate.selectedTradeCurrency = tradeCurrency;
        sepaAccountToCreate.tradeCurrencies = Collections.singletonList(sepaAccountToCreate.selectedTradeCurrency);
        final String bobPaymentAccountId = ApiTestHelper.createPaymentAccount(bobPort, sepaAccountToCreate).extract().body().jsonPath().get("id");
        final TakeOffer payload = new TakeOffer();
        payload.amount = "1";
        payload.paymentAccountId = bobPaymentAccountId;

        final String offerId = given().port(bobPort).when().get("/api/v1/offers").then().extract().body().jsonPath().getString("offers[0].offer_id");
        final String arbitratorAddress = given().port(bobPort).when().get("/api/v1/offers").then().extract().body().jsonPath().getString("offers[0].arbitrators[0]");
        final String aliceAddress = ApiTestHelper.getP2PNetworkStatus(alicePort).address;

        given().
                port(bobPort).
                body(payload).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/offers/" + offerId + "/take").
//
        then().
                statusCode(200).
//                TODO some of following properties change over time and we have no control over that timing so probably there's not much point in returning everything here
        and().body("id", isA(String.class)).
                and().body("offerId", equalTo(offerId)).
                and().body("isCurrencyForTakerFeeBtc", equalTo(true)).
                and().body("txFee", isA(Integer.class)).
                and().body("takerFee", isA(Integer.class)).
                and().body("takeOfferDate", isA(Long.class)).
                and().body("takerFeeTxId", isEmptyOrNullString()).
                and().body("payoutTxId", isEmptyOrNullString()).
                and().body("tradeAmount", equalTo(1)).
                and().body("tradePrice", equalTo(10)).
                and().body("state", isOneOf(ApiTestHelper.toString(Trade.State.values()))).
                and().body("disputeState", equalTo(Trade.DisputeState.NO_DISPUTE.name())).
                and().body("tradePeriodState", equalTo(Trade.TradePeriodState.FIRST_HALF.name())).
                and().body("arbitratorBtcPubKey", isA(String.class)).
                and().body("contractHash", isEmptyOrNullString()).
                and().body("mediatorNodeAddress", equalTo(arbitratorAddress)).
                and().body("takerContractSignature", isEmptyOrNullString()).
                and().body("makerContractSignature", isEmptyOrNullString()).
                and().body("arbitratorNodeAddress", equalTo(arbitratorAddress)).
//                TODO really is this maker address?
        and().body("tradingPeerNodeAddress", equalTo(aliceAddress)).
                and().body("takerPaymentAccountId", equalTo(bobPaymentAccountId)).
                and().body("counterCurrencyTxId", isEmptyOrNullString())
        ;
    }

    @NotNull
    private OfferToCreate getOfferToCreateFixedBuy(String tradeCurrency, String paymentAccountId) {
        final OfferToCreate offer = new OfferToCreate();
        offer.fundUsingBisqWallet = true;
        offer.amount = new BigDecimal(1);
        offer.minAmount = offer.amount;
        offer.direction = OfferPayload.Direction.BUY;
        offer.fixedPrice = 10L;
        offer.marketPair = "BTC_" + tradeCurrency;
        offer.priceType = PriceType.FIXED;
        offer.accountId = paymentAccountId;
        offer.percentageFromMarketPrice = 10.0;
        return offer;
    }

    @NotNull
    private static JSONObject toJsonObject(OfferToCreate offer) {
        final JSONObject jsonOffer = new JSONObject();
        putIfNotNull(jsonOffer, "fundUsingBisqWallet", offer.fundUsingBisqWallet);
        putIfNotNull(jsonOffer, "amount", offer.amount);
        putIfNotNull(jsonOffer, "minAmount", offer.minAmount);
        putIfNotNull(jsonOffer, "direction", offer.direction);
        putIfNotNull(jsonOffer, "fixedPrice", offer.fixedPrice);
        putIfNotNull(jsonOffer, "marketPair", offer.marketPair);
        putIfNotNull(jsonOffer, "priceType", offer.priceType);
        putIfNotNull(jsonOffer, "accountId", offer.accountId);
        putIfNotNull(jsonOffer, "percentageFromMarketPrice", offer.percentageFromMarketPrice);
        return jsonOffer;
    }

    private static void putIfNotNull(JSONObject jsonObject, String key, Object value) {
        if (null == value) {
            return;
        }
        if (value instanceof Enum)
            //noinspection unchecked
            jsonObject.put(key, value.toString());
        else
            //noinspection unchecked
            jsonObject.put(key, value);
    }

    private int getArbitratorPort() {
        return arbitrator.getBindPort(8080);
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

    private int getBobPort() {
        return bob.getBindPort(8080);
    }

}
