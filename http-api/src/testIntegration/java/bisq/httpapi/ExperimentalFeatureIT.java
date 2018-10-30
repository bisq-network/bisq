package bisq.httpapi;

import bisq.httpapi.model.AuthForm;
import bisq.httpapi.model.ChangePassword;
import bisq.httpapi.model.Preferences;
import bisq.httpapi.model.SeedWordsRestore;
import bisq.httpapi.model.WithdrawFundsForm;
import bisq.httpapi.model.payment.SepaPaymentAccount;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;



import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;

@RunWith(Arquillian.class)
public class ExperimentalFeatureIT {

    @DockerContainer
    Container alice = ContainerFactory.createApiContainer("alice", "8080->8080", 3333, false, false, false);

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
    }

    @InSequence(1)
    @Test
    public void getArbitrators_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/arbitrators"));
    }

    @InSequence(1)
    @Test
    public void registerArbitrator_always_returns501() {
        final Response response = given().
                port(getAlicePort()).
                when().
                body("{\"languageCodes\":[\"en\",\"de\"]}").
                contentType(ContentType.JSON).
                post("/api/v1/arbitrators");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void deselectArbitrator_always_returns501() {
        final Response response = given().
                port(getAlicePort()).
                pathParam("address", "abc").
                when().
                post("/api/v1/arbitrators/{address}/select");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void selectArbitrator_always_returns501() {
        final Response response = given().
                port(getAlicePort()).
                pathParam("address", "abc").
                when().
                post("/api/v1/arbitrators/{address}/deselect");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void createBackup_always_returns501() {
        expect501(given().port(getAlicePort()).when().post("/api/v1/backups"));
    }

    @InSequence(1)
    @Test
    public void getBackupList_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/backups"));
    }

    @InSequence(1)
    @Test
    public void getBackup_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/backups/xyz"));
    }

    @InSequence(1)
    @Test
    public void removeBackup_always_returns501() {
        expect501(given().port(getAlicePort()).when().delete("/api/v1/backups/xyz"));
    }

    @InSequence(1)
    @Test
    public void uploadBackup_always_returns501() {
        final Response response = given().
                port(getAlicePort()).
                multiPart("file", "xyz", "abc".getBytes()).
                contentType("multipart/form-data").
                when().
                post("/api/v1/backups/upload");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void restore_always_returns501() {
        expect501(given().port(getAlicePort()).when().post("/api/v1/backups/xyz/restore"));
    }

    @InSequence(1)
    @Test
    public void listClosedTrades_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/closed-tradables"));
    }

    @InSequence(1)
    @Test
    public void getCurrencyList_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/currencies"));
    }


    @InSequence(1)
    @Test
    public void getPriceFeed_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/currencies/prices"));
    }

    @InSequence(1)
    @Test
    public void createPaymentAccount_always_returns501() {
        final SepaPaymentAccount accountToCreate = ApiTestHelper.randomValidCreateSepaAccountPayload();
        final Response response = given().
                port(getAlicePort()).
                contentType(ContentType.JSON).
                body(accountToCreate).
                when().
                post("/api/v1/payment-accounts");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void removePaymentAccountById_always_returns501() {
        expect501(given().port(getAlicePort()).when().delete("/api/v1/payment-accounts/xyz"));
    }

    @InSequence(1)
    @Test
    public void listPaymentAccount_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/payment-accounts"));
    }

    @InSequence(1)
    @Test
    public void getPreferencesAvailableValues_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/preferences/available-values"));
    }

    @InSequence(1)
    @Test
    public void setPreferences_always_returns501() {
        Preferences savedPreferences = new Preferences();
        savedPreferences.autoSelectArbitrators = false;
        savedPreferences.baseCurrencyNetwork = "BTC";
        savedPreferences.blockChainExplorer = "Smartbit";
        savedPreferences.cryptoCurrencies = Arrays.asList("BCH", "ETH");
        savedPreferences.fiatCurrencies = Arrays.asList("PLN", "EUR", "USD");
        savedPreferences.ignoredTraders = Arrays.asList("jes.onion:9999", "ber:3333");
        savedPreferences.maxPriceDistance = 0.5;
        savedPreferences.preferredTradeCurrency = "PLN";
        savedPreferences.useCustomWithdrawalTxFee = true;
        savedPreferences.userCountry = "GB";
        savedPreferences.userLanguage = "PL";
        savedPreferences.withdrawalTxFee = 200L;

        final Response response = given().
                port(getAlicePort()).
                body(savedPreferences).
                contentType(ContentType.JSON).
                when().
                put("/api/v1/preferences");

        expect501(response);
    }

    @InSequence(1)
    @Test
    public void getPreferences_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/preferences"));
    }

    @InSequence(1)
    @Test
    public void authenticate_always_returns501() {
        final Response response = given().
                port(getAlicePort()).
                body(new AuthForm("abc")).
                contentType(ContentType.JSON).
                when().
                post("/api/v1/user/authenticate");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void changePassword_always_returns501() {
        final Response response = given().
                port(getAlicePort()).
                body(new ChangePassword("abc", null)).
                contentType(ContentType.JSON).
                when().
                post("/api/v1/user/password");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void getWalletAddresses_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/wallet/addresses"));
    }

    @InSequence(1)
    @Test
    public void getOrCreateAvailableUnusedWalletAddresses_always_returns501() {
        expect501(given().port(getAlicePort()).when().post("/api/v1/wallet/addresses"));
    }

    @InSequence(1)
    @Test
    public void withdrawFunds_always_returns501() {
        final WithdrawFundsForm data = new WithdrawFundsForm();
        data.amount = 50000000;
        data.feeExcluded = false;
        data.sourceAddresses = Collections.singletonList("abc");
        data.targetAddress = "zyx";
        final Response response = given().port(getAlicePort()).body(data).
                contentType(ContentType.JSON).when().post("/api/v1/wallet/withdraw");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void getSeedWords_always_returns501() {
        expect501(given().port(getAlicePort()).contentType(ContentType.JSON).when().post("/api/v1/wallet/seed-words/retrieve"));
    }

    @InSequence(1)
    @Test
    public void getTransactions_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/wallet/transactions"));
    }

    @InSequence(1)
    @Test
    public void getWalletDetails_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/wallet"));
    }


    @InSequence(1)
    @Test
    public void restoreWalletFromSeedWords_always_returns501() {
        final Response response = given().
                port(getAlicePort()).
                contentType(ContentType.JSON).
                body(new SeedWordsRestore(Collections.singletonList("abc"), "2018-04-28", "abc")).
                when().
                post("/api/v1/wallet/seed-words/restore");
        expect501(response);
    }

    @InSequence(1)
    @Test
    public void getMarkets_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/markets"));
    }

    @InSequence(1)
    @Test
    public void getBitcoinNetworkStatus_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/network/bitcoin/status"));
    }

    @InSequence(1)
    @Test
    public void getP2PNetworkStatus_always_returns501() {
        expect501(given().port(getAlicePort()).when().get("/api/v1/network/p2p/status"));
    }

    private void expect501(Response response) {
        response.then().
                statusCode(501).
                body("errors[0]", equalTo("Experimental features disabled")).
                body("errors.size()", equalTo(1));
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }
}
