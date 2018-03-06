package io.bisq.api;

import io.bisq.api.model.OfferToCreate;
import io.bisq.api.model.PriceType;
import io.bisq.api.model.SepaAccountToCreate;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.restassured.http.ContentType;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;

@RunWith(Arquillian.class)
public class OfferResourceIT {

    @DockerContainer
    Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, true, true);

    @DockerContainer
    Container arbitrator = ContainerFactory.createApiContainer("arbitrator", "8082->8080", 3335, true, true);

    @SuppressWarnings("unused")
    @DockerContainer(order = 4)
    Container seedNode = ContainerFactory.createSeedNodeContainer();

    @DockerContainer
    Container bitcoin = ContainerFactory.createBitcoinContainer();

    private static String paymentAccountId;
    private static String tradeCurrency;


    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws Exception {
        ApiTestHelper.waitForAllServicesToBeReady();
        addPaymentAccount();
    }

    private void addPaymentAccount() {
        final SepaAccountToCreate sepaAccountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload();
        tradeCurrency = sepaAccountToCreate.selectedTradeCurrency;
        paymentAccountId = ApiTestHelper.createPaymentAccount(getAlicePort(), sepaAccountToCreate).extract().body().jsonPath().get("id");
    }

    @InSequence(1)
    @Test
    public void createOffer_noArbitratorAccepted_returns424status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, paymentAccountId);
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
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, paymentAccountId);
        createOffer_template(offer, 427);
    }

    @InSequence(3)
    @Test
    public void createOffer_incompatiblePaymentAccount_returns423status() {
        String otherTradeCurrency = "EUR".equals(tradeCurrency) ? "PLN" : "EUR";
        final OfferToCreate offer = getOfferToCreateFixedBuy(otherTradeCurrency, paymentAccountId);
        createOffer_template(offer, 423);
    }

    @InSequence(3)
    @Test
    public void createOffer_noPaymentAccount_returns425status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, paymentAccountId + paymentAccountId);
        createOffer_template(offer, 425);
    }

    @InSequence(3)
    @Test
    public void createOffer_useMarketBasePriceButNoMarginProvided_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, paymentAccountId);
        offer.priceType = PriceType.PERCENTAGE;
        offer.percentageFromMarketPrice = null;
        createOffer_template(offer, 422);
    }

    @InSequence(3)
    @Test
    public void createOffer_notUseMarketBasePriceButNoFixedPrice_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, paymentAccountId);
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
    public void fundWallet() {
        ApiTestHelper.generateBlocks(bitcoin, 101);
        String walletAddress = ApiTestHelper.getAvailableBtcWalletAddress(getAlicePort());
        ApiTestHelper.sendFunds(bitcoin, walletAddress, 1);
        ApiTestHelper.generateBlocks(bitcoin, 1);
    }

    @InSequence(5)
    @Test
    public void createOffer_validPayloadAndHasFunds_returnsOffer() throws InterruptedException {
        final int alicePort = getAlicePort();

        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, paymentAccountId);

        given().
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
                and().body("offerer", equalTo(ApiTestHelper.getInfo(alicePort).address)).
                and().body("state", equalTo(Offer.State.OFFER_FEE_PAID.name())).
                and().body("btc_amount", equalTo("0.00000001")).
                and().body("min_btc_amount", equalTo("0.00000001")).
                and().body("other_amount", equalTo("0.001")).
                and().body("other_currency", equalTo(tradeCurrency)).
                and().body("price_detail.use_market_price", equalTo(false)).
                and().body("price_detail.market_price_margin", equalTo(offer.percentageFromMarketPrice.floatValue())).
                and().body("direction", equalTo(offer.direction.name()))
        ;
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

}
