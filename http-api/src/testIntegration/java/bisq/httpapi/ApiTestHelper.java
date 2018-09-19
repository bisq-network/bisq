package bisq.httpapi;

import bisq.core.locale.CountryUtil;
import bisq.core.payment.payload.PaymentMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;



import bisq.httpapi.model.ArbitratorList;
import bisq.httpapi.model.Balances;
import bisq.httpapi.model.OfferDetail;
import bisq.httpapi.model.P2PNetworkStatus;
import bisq.httpapi.model.WalletAddressList;
import bisq.httpapi.model.payment.PaymentAccount;
import bisq.httpapi.model.payment.SepaPaymentAccount;
import com.github.javafaker.Faker;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.spi.CubeOutput;
import org.hamcrest.core.AnyOf;

public final class ApiTestHelper {

    public static ValidatableResponse createPaymentAccount(int apiPort, PaymentAccount accountToCreate) {
        return given().
                port(apiPort).
                contentType(ContentType.JSON).
                body(accountToCreate).
//
        when().
                        post("/api/v1/payment-accounts").
//
        then().
                        statusCode(200);
    }

    public static List<String> getAcceptedArbitrators(int apiPort) {
        return given().
                port(apiPort).
                queryParam("acceptedOnly", "true").
//
        when().
                        get("/api/v1/arbitrators").
//
        then().
                        extract().as(ArbitratorList.class).
                        arbitrators.
                        stream().
                        map(arbitrator -> arbitrator.address).
                        collect(Collectors.toList());
    }

    public static Balances getBalance(int apiPort) {
        return given().
                port(apiPort).
//
        when().
                        get("/api/v1/wallet").
//
        then().
                        statusCode(200).
                        extract().body().as(Balances.class);
    }

    public static P2PNetworkStatus getP2PNetworkStatus(int apiPort) {
        return given().
                port(apiPort).
//
        when().
                        get("/api/v1/network/p2p/status").
//
        then().
                        extract().as(P2PNetworkStatus.class);
    }

    public static AnyOf<Object> isIntegerOrLong() {
        return anyOf(instanceOf(Integer.class), instanceOf(Long.class));
    }

    public static ValidatableResponse registerArbitrator(int apiPort) throws InterruptedException {
        final ValidatableResponse validatableResponse = given().
                port(apiPort).
//
        when().
                        body("{\"languageCodes\":[\"en\",\"de\"]}").
                        contentType(ContentType.JSON).
                        post("/api/v1/arbitrators").
//
        then().
                        statusCode(204);

        /* Wait for arbiter registration message to be broadcast across peers*/
        waitForP2PMsgPropagation();

        return validatableResponse;
    }

    public static void waitForP2PMsgPropagation() throws InterruptedException {
        final int P2P_MSG_RELAY_DELAY = 1000;
        Thread.sleep(P2P_MSG_RELAY_DELAY);
    }

    public static void waitForAllServicesToBeReady() throws InterruptedException {
//        TODO it would be nice to expose endpoint that would respond with 200
        /**
         * PaymentMethod initializes it's static values after all services get initialized
         */
        final int ALL_SERVICES_INITIALIZED_DELAY = 5000;
        Thread.sleep(ALL_SERVICES_INITIALIZED_DELAY);
    }

    public static SepaPaymentAccount randomValidCreateSepaAccountPayload(String tradeCurrency, String countryCode) {
        final Faker faker = new Faker();
        final SepaPaymentAccount accountToCreate = new SepaPaymentAccount();
        if (null == countryCode)
            countryCode = faker.options().nextElement(CountryUtil.getAllSepaCountries()).code;
        accountToCreate.paymentMethod = PaymentMethod.SEPA_ID;
        accountToCreate.accountName = faker.commerce().productName();
        accountToCreate.bic = faker.finance().bic();
        accountToCreate.iban = faker.finance().iban();
        accountToCreate.holderName = faker.name().fullName();
        accountToCreate.countryCode = countryCode;
        accountToCreate.acceptedCountries = new ArrayList<>(new HashSet<>(Arrays.asList("PL", "GB", countryCode)));
        accountToCreate.selectedTradeCurrency = faker.options().option("PLN", "USD", "EUR", "GBP");
        if (null != tradeCurrency)
            accountToCreate.selectedTradeCurrency = tradeCurrency;
        accountToCreate.tradeCurrencies = Collections.singletonList(accountToCreate.selectedTradeCurrency);
        return accountToCreate;
    }

    public static void randomizeAccountPayload(PaymentAccount accountToCreate) {
        final Faker faker = new Faker();
        accountToCreate.accountName = faker.commerce().productName();
        accountToCreate.selectedTradeCurrency = faker.options().option("PLN", "USD", "EUR", "GBP");
        accountToCreate.tradeCurrencies = Collections.singletonList(accountToCreate.selectedTradeCurrency);
    }

    public static SepaPaymentAccount randomValidCreateSepaAccountPayload() {
        return randomValidCreateSepaAccountPayload(null, null);
    }

    public static void generateBlocks(Container bitcoin, int numberOfBlocks) {
        final CubeOutput cubeOutput = bitcoin.exec("bitcoin-cli", "-regtest", "generate", "" + numberOfBlocks);
        assertEquals("Command 'generate blocks' should succeed", "", cubeOutput.getError());
        final int ALL_SERVICES_INITIALIZED_DELAY = 3000;
        try {
            Thread.sleep(ALL_SERVICES_INITIALIZED_DELAY);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getAvailableBtcWalletAddress(int apiPort) {
        return given().
                port(apiPort).
//
        when().
                        post("/api/v1/wallet/addresses").
//
        then().
                        statusCode(200)
                .extract().body().jsonPath().getString("address");
    }

    public static WalletAddressList getBtcWalletAddresses(int apiPort) {
        return given().
                port(apiPort).
//
        when().
                        get("/api/v1/wallet/addresses").
//
        then().
                        statusCode(200)
                .extract().body().as(WalletAddressList.class);
    }


    public static void sendFunds(Container bitcoin, String walletAddress, double amount) {
        final CubeOutput cubeOutput = bitcoin.exec("bitcoin-cli", "-regtest", "sendtoaddress", walletAddress, "" + amount);
        assertEquals("Command 'sendfrom' should succeed", "", cubeOutput.getError());
    }

    public static String[] toString(Enum[] values) {
        final String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].name();
        }
        return result;
    }

    public static void deselectAllArbitrators(int apiPort) {
        getArbitrators(apiPort).stream().forEach(arbitratorAddress -> deselectArbitrator(apiPort, arbitratorAddress));
    }

    private static ValidatableResponse deselectArbitrator(int apiPort, String arbitratorAddress) {
        return given().
                port(apiPort).
//
        when().
                        post("/api/v1/arbitrators/" + arbitratorAddress + "/deselect").
//
        then().
                        statusCode(200)
                ;
    }

    public static ValidatableResponse selectArbitrator(int apiPort, String arbitratorAddress) {
        return given().
                port(apiPort).
//
        when().
                        post("/api/v1/arbitrators/" + arbitratorAddress + "/select").
//
        then().
                        statusCode(200)
                ;
    }

    public static List<String> getArbitrators(int apiPort) {
        return given().
                port(apiPort).
//
        when().
                        get("/api/v1/arbitrators").
//
        then().
                        extract().as(ArbitratorList.class).
                        arbitrators.
                        stream().
                        map(arbitrator -> arbitrator.address).
                        collect(Collectors.toList());
    }

    public static OfferDetail getOfferById(int apiPort, String offerId) {
        return given().
                port(apiPort).
//
        when().
                        get("/api/v1/offers/" + offerId).
//
        then().
                        statusCode(200).
                        extract().as(OfferDetail.class)
                ;
    }
}
