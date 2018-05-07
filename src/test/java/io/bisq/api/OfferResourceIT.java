package io.bisq.api;

import io.bisq.api.model.OfferDetail;
import io.bisq.api.model.OfferToCreate;
import io.bisq.api.model.PriceType;
import io.bisq.api.model.TakeOffer;
import io.bisq.api.model.payment.SepaPaymentAccount;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.Trade;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
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

    static SepaPaymentAccount alicePaymentAccount;
    static SepaPaymentAccount bobPaymentAccount;
    private static String bobIncompatiblePaymentAccountId;
    private static String tradeCurrency;
    static OfferDetail createdOffer;

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
        final String tradePaymentMethodCountry = sepaAccountToCreate.countryCode;
        alicePaymentAccount = ApiTestHelper.createPaymentAccount(alicePort, sepaAccountToCreate).extract().as(SepaPaymentAccount.class);

        sepaAccountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload(tradeCurrency, tradePaymentMethodCountry);
        bobPaymentAccount = ApiTestHelper.createPaymentAccount(bobPort, sepaAccountToCreate).extract().as(SepaPaymentAccount.class);

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

    private void createOffer_template(OfferToCreate offer, int expectedStatusCode, String errorMessage) {
        createOffer_template(offer, expectedStatusCode).
                and().body("errors.size()", equalTo(1)).
                and().body("errors[0]", equalTo(errorMessage))
        ;
    }

    private ValidatableResponse createOffer_template(OfferToCreate offer, int expectedStatusCode) {
        return given().
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
        offer.priceType = PriceType.PERCENTAGE.name();
        offer.percentageFromMarketPrice = null;
        createOffer_template(offer, 422);
    }

    @InSequence(3)
    @Test
    public void createOffer_notUseMarketBasePriceButNoFixedPrice_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.priceType = PriceType.FIXED.name();
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

    @InSequence(3)
    @Test
    public void createOffer_invalidDirection_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.direction = OfferPayload.Direction.BUY.name() + OfferPayload.Direction.SELL.name();
        createOffer_template(offer, 422, "direction must be one of: BUY, SELL");
    }

    @InSequence(3)
    @Test
    public void createOffer_missingDirection_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.direction = null;
        createOffer_template(offer, 422, "direction may not be null");
    }

    @InSequence(3)
    @Test
    public void createOffer_invalidPriceType_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.priceType = PriceType.FIXED.name() + PriceType.PERCENTAGE.name();
        createOffer_template(offer, 422, "priceType must be one of: FIXED, PERCENTAGE");
    }

    @InSequence(3)
    @Test
    public void createOffer_missingPriceType_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.priceType = null;
        createOffer_template(offer, 422, "priceType may not be null");
    }

    @InSequence(3)
    @Test
    public void createOffer_fixedPriceNegative_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.fixedPrice = -1;
        createOffer_template(offer, 422, "fixedPrice must be greater than or equal to 0");
    }

    @InSequence(3)
    @Test
    public void createOffer_fixedPriceZero_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.fixedPrice = 0;
        createOffer_template(offer, 422, "When choosing FIXED price, fill in fixedPrice with a price > 0");
    }

    @InSequence(3)
    @Test
    public void createOffer_amountZero_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.amount = 0;
        createOffer_template(offer, 422, "amount must be greater than or equal to 1");
    }

    @InSequence(3)
    @Test
    public void createOffer_minAmountZero_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.minAmount = 0;
        createOffer_template(offer, 422, "minAmount must be greater than or equal to 1");
    }

    @InSequence(3)
    @Test
    public void createOffer_buyerSecurityDepositZero_returns422status() {
        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.buyerSecurityDeposit = 0L;
        createOffer_template(offer, 422, "buyerSecurityDeposit must be greater than or equal to 1");
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
    public void createOffer_amountTooHigh_returns426() {
        final int alicePort = getAlicePort();

        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.amount = 100000000;

        given().
                port(alicePort).
                body(offer).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/offers").
//
        then().
                statusCode(426);
    }

    @InSequence(6)
    @Test
    public void createOffer_validPayloadAndHasFunds_returnsOffer() {
        final int alicePort = getAlicePort();

        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);

        createdOffer = given().
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
                        and().body("amount", equalTo(6250000)).
                        and().body("arbitratorNodeAddresses", equalTo(ApiTestHelper.getAcceptedArbitrators(alicePort))).
                        and().body("baseCurrencyCode", equalTo("BTC")).
                        and().body("bankId", equalTo(alicePaymentAccount.bic)).
                        and().body("blockHeightAtOfferCreation", isA(Integer.class)).
                        and().body("buyerSecurityDeposit", equalTo(offer.buyerSecurityDeposit.intValue())).
                        and().body("counterCurrencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                        and().body("countryCode", equalTo(alicePaymentAccount.countryCode)).
                        and().body("currencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                        and().body("extraDataMap.accountAgeWitnessHash", isA(String.class)).
                        and().body("date", isA(Long.class)).
                        and().body("direction", equalTo(OfferPayload.Direction.BUY.name())).
                        and().body("id", isA(String.class)).
                        and().body("isCurrencyForMakerFeeBtc", equalTo(true)).
                        and().body("isPrivateOffer", equalTo(false)).
                        and().body("lowerClosePrice", equalTo(0)).
                        and().body("makerFee", equalTo(12500)).
                        and().body("makerPaymentAccountId", equalTo(alicePaymentAccount.id)).
                        and().body("marketPriceMargin", equalTo(0f)).
                        and().body("maxTradeLimit", equalTo(25000000)).
                        and().body("maxTradePeriod", equalTo(518400000)).
                        and().body("minAmount", equalTo(6250000)).
                        and().body("offerFeePaymentTxId", isA(String.class)).
                        and().body("ownerNodeAddress", equalTo(ApiTestHelper.getP2PNetworkStatus(alicePort).address)).
                        and().body("paymentMethodId", equalTo(alicePaymentAccount.paymentMethod)).
                        and().body("price", equalTo(10)).
                        and().body("protocolVersion", equalTo(1)).
                        and().body("sellerSecurityDeposit", equalTo(300000)).
                        and().body("state", equalTo(Offer.State.OFFER_FEE_PAID.name())).
                        and().body("txFee", isA(Integer.class)).
                        and().body("upperClosePrice", equalTo(0)).
                        and().body("useAutoClose", equalTo(false)).
                        and().body("useMarketBasedPrice", equalTo(false)).
                        and().body("useReOpenAfterAutoClose", equalTo(false)).
                        and().body("versionNr", isA(String.class)).
                        extract().as(OfferDetail.class);
    }

    @InSequence(7)
    @Test
    public void listOffers_always_returnsOffers() {
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
                and().body("offers[0].amount", equalTo(6250000)).
                and().body("offers[0].arbitratorNodeAddresses", equalTo(ApiTestHelper.getAcceptedArbitrators(alicePort))).
                and().body("offers[0].baseCurrencyCode", equalTo("BTC")).
                and().body("offers[0].bankId", equalTo(alicePaymentAccount.bic)).
                and().body("offers[0].blockHeightAtOfferCreation", isA(Integer.class)).
                and().body("offers[0].buyerSecurityDeposit", isA(Integer.class)).
                and().body("offers[0].counterCurrencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("offers[0].countryCode", equalTo(alicePaymentAccount.countryCode)).
                and().body("offers[0].currencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("offers[0].date", isA(Long.class)).
                and().body("offers[0].direction", equalTo(OfferPayload.Direction.BUY.name())).
                and().body("offers[0].id", isA(String.class)).
                and().body("offers[0].isCurrencyForMakerFeeBtc", equalTo(true)).
                and().body("offers[0].isPrivateOffer", equalTo(false)).
                and().body("offers[0].lowerClosePrice", equalTo(0)).
                and().body("offers[0].makerFee", equalTo(12500)).
                and().body("offers[0].makerPaymentAccountId", equalTo(alicePaymentAccount.id)).
                and().body("offers[0].marketPriceMargin", equalTo(0f)).
                and().body("offers[0].maxTradeLimit", equalTo(25000000)).
                and().body("offers[0].maxTradePeriod", equalTo(518400000)).
                and().body("offers[0].minAmount", equalTo(6250000)).
                and().body("offers[0].offerFeePaymentTxId", isA(String.class)).
                and().body("offers[0].ownerNodeAddress", equalTo(ApiTestHelper.getP2PNetworkStatus(alicePort).address)).
                and().body("offers[0].paymentMethodId", equalTo(alicePaymentAccount.paymentMethod)).
                and().body("offers[0].price", equalTo(10)).
                and().body("offers[0].protocolVersion", equalTo(1)).
                and().body("offers[0].sellerSecurityDeposit", equalTo(300000)).
                and().body("offers[0].state", isA(String.class)).
                and().body("offers[0].txFee", isA(Integer.class)).
                and().body("offers[0].upperClosePrice", equalTo(0)).
                and().body("offers[0].useAutoClose", equalTo(false)).
                and().body("offers[0].useMarketBasedPrice", equalTo(false)).
                and().body("offers[0].useReOpenAfterAutoClose", equalTo(false)).
                and().body("offers[0].versionNr", isA(String.class))
        ;
    }

    @InSequence(8)
    @Test
    public void createOffer_validMarketPriceBasedOfferAndHasFunds_returnsOffer() throws Exception {
        final int alicePort = getAlicePort();

        final OfferToCreate offer = getOfferToCreateFixedBuy(tradeCurrency, alicePaymentAccount.id);
        offer.fixedPrice = 0;
        offer.percentageFromMarketPrice = new BigDecimal(.12);
        offer.priceType = PriceType.PERCENTAGE.name();

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
                and().body("acceptedCountryCodes", equalTo(alicePaymentAccount.acceptedCountries)).
                and().body("amount", equalTo(6250000)).
                and().body("arbitratorNodeAddresses", equalTo(ApiTestHelper.getAcceptedArbitrators(alicePort))).
                and().body("baseCurrencyCode", equalTo("BTC")).
                and().body("bankId", equalTo(alicePaymentAccount.bic)).
                and().body("blockHeightAtOfferCreation", isA(Integer.class)).
                and().body("buyerSecurityDeposit", equalTo(offer.buyerSecurityDeposit.intValue())).
                and().body("counterCurrencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("countryCode", equalTo(alicePaymentAccount.countryCode)).
                and().body("currencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("date", isA(Long.class)).
                and().body("direction", equalTo(OfferPayload.Direction.BUY.name())).
                and().body("id", isA(String.class)).
                and().body("isCurrencyForMakerFeeBtc", equalTo(true)).
                and().body("isPrivateOffer", equalTo(false)).
                and().body("lowerClosePrice", equalTo(0)).
                and().body("makerFee", equalTo(12500)).
                and().body("makerPaymentAccountId", equalTo(alicePaymentAccount.id)).
                and().body("marketPriceMargin", equalTo(.12f)).
                and().body("maxTradeLimit", equalTo(25000000)).
                and().body("maxTradePeriod", equalTo(518400000)).
                and().body("minAmount", equalTo(6250000)).
                and().body("offerFeePaymentTxId", isA(String.class)).
                and().body("ownerNodeAddress", equalTo(ApiTestHelper.getP2PNetworkStatus(alicePort).address)).
                and().body("paymentMethodId", equalTo(alicePaymentAccount.paymentMethod)).
                and().body("price", equalTo(0)).
                and().body("protocolVersion", equalTo(1)).
                and().body("sellerSecurityDeposit", equalTo(300000)).
                and().body("state", equalTo(Offer.State.OFFER_FEE_PAID.name())).
                and().body("txFee", isA(Integer.class)).
                and().body("upperClosePrice", equalTo(0)).
                and().body("useAutoClose", equalTo(false)).
                and().body("useMarketBasedPrice", equalTo(true)).
                and().body("useReOpenAfterAutoClose", equalTo(false)).
                and().body("versionNr", isA(String.class));
    }

    @InSequence(9)
    @Test
    public void takeOffer_offerNotFound_returns404status() {
        final TakeOffer payload = new TakeOffer(bobPaymentAccount.id, 1);
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

    @InSequence(9)
    @Test
    public void takeOffer_validPayloadButNoFunds_returns427status() {
        final TakeOffer payload = new TakeOffer(bobPaymentAccount.id, 1);
        takeOffer_template(createdOffer.id, payload, 427);
    }

    @InSequence(9)
    @Test
    public void takeOffer_paymentAccountIdMissing_returns422status() {
        final TakeOffer payload = new TakeOffer(null, 1);
        takeOffer_template(createdOffer.id, payload, 422);
    }

    @InSequence(9)
    @Test
    public void takeOffer_amountMissing_returns422() {
        final TakeOffer payload = new TakeOffer(bobPaymentAccount.id, 1);
        final JSONObject jsonPayload = toJsonObject(payload);
        jsonPayload.remove("amount");
        given().
                port(getBobPort()).
                body(jsonPayload).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/offers/" + createdOffer.id + "/take").
//
        then().
                statusCode(422)
        ;
    }

    @InSequence(9)
    @Test
    public void takeOffer_paymentAccountNotFound_returns425() {
        final TakeOffer payload = new TakeOffer("non-existing-account", 1);
        takeOffer_template(createdOffer.id, payload, 425);
    }

    @InSequence(9)
    @Test
    public void takeOffer_incompatiblePaymentAccount_returns423() {
        final TakeOffer payload = new TakeOffer(bobIncompatiblePaymentAccountId, 1);
        takeOffer_template(createdOffer.id, payload, 423);
    }

    @Ignore("Bug in tradeManager.onTakeOffer which resolves instead of reject in this scenario")
    @InSequence(9)
    @Test
    public void takeOffer_noArbitratorSelected_returns424() {
        ApiTestHelper.deselectAllArbitrators(getBobPort());
        final TakeOffer payload = new TakeOffer(bobPaymentAccount.id, 1);
        takeOffer_template(createdOffer.id, payload, 423);
    }

    @Ignore("Bug in tradeManager.onTakeOffer which resolves instead of reject in this scenario")
    @InSequence(9)
    @Test
    public void takeOffer_noOverlappingArbitrator_returnsXXX() throws Exception {
        final int bobPort = getBobPort();
        ApiTestHelper.registerArbitrator(getAlicePort());
        final OfferDetail offer = ApiTestHelper.getOfferById(bobPort, createdOffer.id);
        final List<String> arbitrators = ApiTestHelper.getAcceptedArbitrators(bobPort);
        arbitrators.removeAll(offer.arbitratorNodeAddresses);
        Assert.assertThat(arbitrators.size(), greaterThan(0));
        ApiTestHelper.deselectAllArbitrators(bobPort);
        ApiTestHelper.selectArbitrator(bobPort, arbitrators.get(0));

        final TakeOffer payload = new TakeOffer(bobPaymentAccount.id, 1);
        takeOffer_template(createdOffer.id, payload, 0);
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @InSequence(10)
    @Test
    public void selectSameArbitratorAsInOffer() {
        final int bobPort = getBobPort();
        final OfferDetail offer = ApiTestHelper.getOfferById(bobPort, createdOffer.id);
        Assert.assertThat(offer.arbitratorNodeAddresses.size(), greaterThan(0));
        ApiTestHelper.selectArbitrator(bobPort, offer.arbitratorNodeAddresses.get(0));
    }

    @InSequence(11)
    @Test
    public void fundBobWallet() {
        ApiTestHelper.generateBlocks(bitcoin, 101);
        String walletAddress = ApiTestHelper.getAvailableBtcWalletAddress(getBobPort());
        ApiTestHelper.sendFunds(bitcoin, walletAddress, 1);
        ApiTestHelper.generateBlocks(bitcoin, 1);
    }

    @InSequence(12)
    @Test
    public void takeOffer_takerSameAsMaker_returns428() {
        final int alicePort = getAlicePort();

        final TakeOffer payload = new TakeOffer();
        payload.amount = 6250000;
        payload.paymentAccountId = bobPaymentAccount.id;

        final String offerId = createdOffer.id;

        given().
                port(alicePort).
                body(payload).
                contentType(ContentType.JSON).
//
        when().
                post("/api/v1/offers/" + offerId + "/take").
//
        then().
                statusCode(428)
        ;
    }

    @InSequence(13)
    @Test
    public void takeOffer_validPaymentMethodAndHasFunds_returnsTrade() {
        final int alicePort = getAlicePort();
        final int bobPort = getBobPort();

        final TakeOffer payload = new TakeOffer();
        payload.amount = 6250000;
        payload.paymentAccountId = bobPaymentAccount.id;

        final String offerId = createdOffer.id;
        final String arbitratorAddress = createdOffer.arbitratorNodeAddresses.get(0);
        final String aliceAddress = ApiTestHelper.getP2PNetworkStatus(alicePort).address;

//        TODO some of following properties change over time and we have no control over that timing so probably there's not much point in returning everything here
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
                and().body("id", isA(String.class)).

                and().body("offer.id", equalTo(offerId)).
                and().body("offer.acceptedCountryCodes", equalTo(alicePaymentAccount.acceptedCountries)).
                and().body("offer.amount", equalTo(6250000)).
                and().body("offer.arbitratorNodeAddresses", equalTo(ApiTestHelper.getAcceptedArbitrators(alicePort))).
                and().body("offer.baseCurrencyCode", equalTo("BTC")).
                and().body("offer.bankId", equalTo(alicePaymentAccount.bic)).
                and().body("offer.blockHeightAtOfferCreation", isA(Integer.class)).
                and().body("offer.buyerSecurityDeposit", isA(Integer.class)).
                and().body("offer.counterCurrencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("offer.countryCode", equalTo(alicePaymentAccount.countryCode)).
                and().body("offer.currencyCode", equalTo(alicePaymentAccount.selectedTradeCurrency)).
                and().body("offer.date", isA(Long.class)).
                and().body("offer.direction", equalTo(OfferPayload.Direction.BUY.name())).
                and().body("offer.id", isA(String.class)).
                and().body("offer.isCurrencyForMakerFeeBtc", equalTo(true)).
                and().body("offer.isPrivateOffer", equalTo(false)).
                and().body("offer.lowerClosePrice", equalTo(0)).
                and().body("offer.makerFee", equalTo(12500)).
                and().body("offer.makerPaymentAccountId", equalTo(alicePaymentAccount.id)).
                and().body("offer.marketPriceMargin", equalTo(0f)).
                and().body("offer.maxTradeLimit", equalTo(25000000)).
                and().body("offer.maxTradePeriod", equalTo(518400000)).
                and().body("offer.minAmount", equalTo(6250000)).
                and().body("offer.offerFeePaymentTxId", isA(String.class)).
                and().body("offer.ownerNodeAddress", equalTo(ApiTestHelper.getP2PNetworkStatus(alicePort).address)).
                and().body("offer.paymentMethodId", equalTo(alicePaymentAccount.paymentMethod)).
                and().body("offer.price", equalTo(10)).
                and().body("offer.protocolVersion", equalTo(1)).
                and().body("offer.sellerSecurityDeposit", equalTo(300000)).
                and().body("offer.state", equalTo(Offer.State.AVAILABLE.name())).
                and().body("offer.txFee", isA(Integer.class)).
                and().body("offer.upperClosePrice", equalTo(0)).
                and().body("offer.useAutoClose", equalTo(false)).
                and().body("offer.useMarketBasedPrice", equalTo(false)).
                and().body("offer.useReOpenAfterAutoClose", equalTo(false)).
                and().body("offer.versionNr", isA(String.class)).

                and().body("isCurrencyForTakerFeeBtc", equalTo(true)).
                and().body("txFee", isA(Integer.class)).
                and().body("takerFee", isA(Integer.class)).
                and().body("takeOfferDate", isA(Long.class)).
                and().body("takerFeeTxId", isEmptyOrNullString()).
                and().body("payoutTxId", isEmptyOrNullString()).
                and().body("tradeAmount", equalTo(6250000)).
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
                and().body("tradingPeerNodeAddress", equalTo(aliceAddress)).
                and().body("takerPaymentAccountId", equalTo(bobPaymentAccount.id)).
                and().body("counterCurrencyTxId", isEmptyOrNullString())
        ;
    }

    @InSequence(14)
    @Test
    public void cancelOffer_notMyOffer_returns404() throws Exception {
        createOffer_validPayloadAndHasFunds_returnsOffer();
        ApiTestHelper.waitForP2PMsgPropagation();
        final int bobPort = getBobPort();
        assertOfferExists(bobPort, createdOffer.id);
        given().
                port(bobPort).
//
        when().
                delete("/api/v1/offers/" + createdOffer.id).
//
        then().
                statusCode(404)
        ;
        assertOfferExists(bobPort, createdOffer.id);
    }

    @InSequence(15)
    @Test
    public void cancelOffer_ownExistingOffer_returns200() throws Exception {
        final int alicePort = getAlicePort();
        given().
                port(alicePort).
//
        when().
                delete("/api/v1/offers/" + createdOffer.id).
//
        then().
                statusCode(200)
        ;
        given().
                port(alicePort).
//
        when().
                get("/api/v1/offers/" + createdOffer.id).
//
        then().
                statusCode(404)
        ;
    }

    @InSequence(16)
    @Test
    public void cancelOffer_ownNonExistingOffer_returns404() throws Exception {
        final int alicePort = getAlicePort();
        given().
                port(alicePort).
//
        when().
                delete("/api/v1/offers/" + createdOffer.id + createdOffer.id).
//
        then().
                statusCode(404)
        ;
    }

    @NotNull
    private OfferToCreate getOfferToCreateFixedBuy(String tradeCurrency, String paymentAccountId) {
        final OfferToCreate offer = new OfferToCreate();
        offer.fundUsingBisqWallet = true;
        offer.amount = 6250000;
        offer.minAmount = offer.amount;
        offer.direction = OfferPayload.Direction.BUY.name();
        offer.fixedPrice = 10L;
        offer.marketPair = "BTC_" + tradeCurrency;
        offer.priceType = PriceType.FIXED.name();
        offer.accountId = paymentAccountId;
        offer.buyerSecurityDeposit = 123456L;
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

    @NotNull
    private JSONObject toJsonObject(TakeOffer payload) {
        final JSONObject json = new JSONObject();
        putIfNotNull(json, "paymentAccountId", payload.paymentAccountId);
        putIfNotNull(json, "amount", payload.amount);
        return json;
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

    private static void assertOfferExists(int apiPort, String offerId) {
        given().
                port(apiPort).
//
        when().
                get("/api/v1/offers/" + offerId).
//
        then().
                statusCode(200)
        ;
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
