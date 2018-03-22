package io.bisq.api;

import io.bisq.api.model.OfferDetail;
import io.bisq.api.model.OfferToCreate;
import io.bisq.api.model.PriceType;
import io.bisq.api.model.TakeOffer;
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

    private static SepaPaymentAccount alicePaymentAccount;
    private static String bobPaymentAccountId;
    private static String bobIncompatiblePaymentAccountId;
    private static String tradeCurrency;
    private static String tradePaymentMethodCountry;
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
        tradePaymentMethodCountry = sepaAccountToCreate.countryCode;
        alicePaymentAccount = ApiTestHelper.createPaymentAccount(alicePort, sepaAccountToCreate).extract().as(SepaPaymentAccount.class);

        sepaAccountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload(tradeCurrency, tradePaymentMethodCountry);
        bobPaymentAccountId = ApiTestHelper.createPaymentAccount(bobPort, sepaAccountToCreate).extract().body().jsonPath().get("id");

        final String incompatibleCurrency = "EUR".equals(tradeCurrency) ? "PLN" : "EUR";
        sepaAccountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload(incompatibleCurrency, tradePaymentMethodCountry);
        bobIncompatiblePaymentAccountId = ApiTestHelper.createPaymentAccount(bobPort, sepaAccountToCreate).extract().body().jsonPath().get("id");
    }

    @InSequence(1)
    @Test
    public void createOffer_noArbitratorAccepted_returns424status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
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
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        createOffer_template(offer, 427);
    }

    @InSequence(3)
    @Test
    public void createOffer_incompatiblePaymentAccount_returns423status() {
        String otherTradeCurrency = "EUR".equals(tradeCurrency) ? "PLN" : "EUR";
        final OfferToCreate offer = getOfferToCreateFixedBuy(otherTradeCurrency, alicePaymentAccount.id);
        createOffer_template(offer, 423);
    }

    @InSequence(3)
    @Test
    public void createOffer_noPaymentAccount_returns425status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id + alicePaymentAccount.id);
        createOffer_template(offer, 425);
    }

    @InSequence(3)
    @Test
    public void createOffer_useMarketBasePriceButNoMarginProvided_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.priceType = PriceType.PERCENTAGE;
        offer.percentageFromMarketPrice = null;
        createOffer_template(offer, 422);
    }

    @InSequence(3)
    @Test
    public void createOffer_notUseMarketBasePriceButNoFixedPrice_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
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

        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);

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
                        and().body("acceptedCountryCodes", equalTo(alicePaymentAccount.acceptedCountries)).
                        and().body("amount", equalTo(1)).
                        and().body("arbitratorNodeAddresses", equalTo(ApiTestHelper.getAcceptedArbitrators(alicePort))).
                        and().body("baseCurrencyCode", equalTo("BTC")).
                        and().body("bankId", equalTo(alicePaymentAccount.bic)).
                        and().body("blockHeightAtOfferCreation", isA(Integer.class)).
                        and().body("buyerSecurityDeposit", equalTo(1000000)).
                        and().body("counterCurrencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                        and().body("countryCode", equalTo(alicePaymentAccount.countryCode)).
                        and().body("currencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                        and().body("date", isA(Long.class)).
                        and().body("direction", equalTo(OfferPayload.Direction.BUY.name())).
                        and().body("id", isA(String.class)).
                        and().body("isCurrencyForMakerFeeBtc", equalTo(true)).
                        and().body("isPrivateOffer", equalTo(false)).
                        and().body("lowerClosePrice", equalTo(0)).
                        and().body("makerFee", equalTo(5000)).
                        and().body("makerPaymentAccountId", equalTo(alicePaymentAccount.id)).
                        and().body("marketPriceMargin", equalTo(10f)).
                        and().body("maxTradeLimit", equalTo(25000000)).
                        and().body("maxTradePeriod", equalTo(518400000)).
                        and().body("minAmount", equalTo(1)).
                        and().body("offerFeePaymentTxId", isA(String.class)).
                        and().body("ownerNodeAddress", equalTo(ApiTestHelper.getP2PNetworkStatus(alicePort).address)).
                        and().body("paymentMethodId", equalTo(alicePaymentAccount.paymentMethod)).
                        and().body("price", equalTo(10)).
                        and().body("protocolVersion", equalTo(1)).
                        and().body("sellerSecurityDeposit", equalTo(300000)).
                        and().body("state", equalTo(Offer.State.OFFER_FEE_PAID.name())).
                        and().body("txFee", equalTo(6000)).
                        and().body("upperClosePrice", equalTo(0)).
                        and().body("useAutoClose", equalTo(false)).
                        and().body("useMarketBasedPrice", equalTo(false)).
                        and().body("useReOpenAfterAutoClose", equalTo(false)).
                        and().body("versionNr", isA(String.class)).
                        extract().jsonPath().getString("id");
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
                and().body("offers[0].acceptedCountryCodes", equalTo(alicePaymentAccount.acceptedCountries)).
                and().body("offers[0].amount", equalTo(1)).
                and().body("offers[0].arbitratorNodeAddresses", equalTo(ApiTestHelper.getAcceptedArbitrators(alicePort))).
                and().body("offers[0].baseCurrencyCode", equalTo("BTC")).
                and().body("offers[0].bankId", equalTo(alicePaymentAccount.bic)).
                and().body("offers[0].blockHeightAtOfferCreation", isA(Integer.class)).
                and().body("offers[0].buyerSecurityDeposit", equalTo(1000000)).
                and().body("offers[0].counterCurrencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("offers[0].countryCode", equalTo(alicePaymentAccount.countryCode)).
                and().body("offers[0].currencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("offers[0].date", isA(Long.class)).
                and().body("offers[0].direction", equalTo(OfferPayload.Direction.BUY.name())).
                and().body("offers[0].id", isA(String.class)).
                and().body("offers[0].isCurrencyForMakerFeeBtc", equalTo(true)).
                and().body("offers[0].isPrivateOffer", equalTo(false)).
                and().body("offers[0].lowerClosePrice", equalTo(0)).
                and().body("offers[0].makerFee", equalTo(5000)).
                and().body("offers[0].makerPaymentAccountId", equalTo(alicePaymentAccount.id)).
                and().body("offers[0].marketPriceMargin", equalTo(10f)).
                and().body("offers[0].maxTradeLimit", equalTo(25000000)).
                and().body("offers[0].maxTradePeriod", equalTo(518400000)).
                and().body("offers[0].minAmount", equalTo(1)).
                and().body("offers[0].offerFeePaymentTxId", isA(String.class)).
                and().body("offers[0].ownerNodeAddress", equalTo(ApiTestHelper.getP2PNetworkStatus(alicePort).address)).
                and().body("offers[0].paymentMethodId", equalTo(alicePaymentAccount.paymentMethod)).
                and().body("offers[0].price", equalTo(10)).
                and().body("offers[0].protocolVersion", equalTo(1)).
                and().body("offers[0].sellerSecurityDeposit", equalTo(300000)).
                and().body("offers[0].state", isA(String.class)).
                and().body("offers[0].txFee", equalTo(6000)).
                and().body("offers[0].upperClosePrice", equalTo(0)).
                and().body("offers[0].useAutoClose", equalTo(false)).
                and().body("offers[0].useMarketBasedPrice", equalTo(false)).
                and().body("offers[0].useReOpenAfterAutoClose", equalTo(false)).
                and().body("offers[0].versionNr", isA(String.class))
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
        arbitrators.removeAll(offer.arbitratorNodeAddresses);
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
        Assert.assertThat(offer.arbitratorNodeAddresses.size(), greaterThan(0));
        ApiTestHelper.selectArbitrator(bobPort, offer.arbitratorNodeAddresses.get(0));
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

        final SepaPaymentAccount sepaAccountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload(tradeCurrency, tradePaymentMethodCountry);
        sepaAccountToCreate.selectedTradeCurrency = tradeCurrency;
        sepaAccountToCreate.tradeCurrencies = Collections.singletonList(sepaAccountToCreate.selectedTradeCurrency);
        final String bobPaymentAccountId = ApiTestHelper.createPaymentAccount(bobPort, sepaAccountToCreate).extract().body().jsonPath().get("id");
        final TakeOffer payload = new TakeOffer();
        payload.amount = "1";
        payload.paymentAccountId = bobPaymentAccountId;

        final String offerId = given().port(bobPort).when().get("/api/v1/offers").then().extract().body().jsonPath().getString("offers[0].id");
        final String arbitratorAddress = given().port(bobPort).when().get("/api/v1/offers").then().extract().body().jsonPath().getString("offers[0].arbitratorNodeAddresses[0]");
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
