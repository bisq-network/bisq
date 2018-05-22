package network.bisq.api;

import network.bisq.api.model.Preferences;
import io.restassured.http.ContentType;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;

@RunWith(Arquillian.class)
public class PreferencesResourceIT {

    @DockerContainer
    Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, false);

    private static Preferences savedPreferences;

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        /**
         * PaymentMethod initializes it's static values after all services get initialized
         */
        ApiTestHelper.waitForAllServicesToBeReady();
    }

    @InSequence(1)
    @Test
    public void getPreferencesAvailableValues_always_returns200() {
        given().
                port(getAlicePort()).
//
        when().
                get("/api/v1/preferences/available-values").
//
        then().
                statusCode(200).
                body("blockChainExplorers", isA(List.class)).
                body("blockChainExplorers[0]", isA(String.class)).
                body("cryptoCurrencies", isA(List.class)).
                body("cryptoCurrencies[0]", isA(String.class)).
                body("fiatCurrencies", isA(List.class)).
                body("fiatCurrencies[0]", isA(String.class)).
                body("userCountries", isA(List.class)).
                body("userCountries[0]", isA(String.class))
        ;
    }

    @InSequence(1)
    @Test
    public void setPreferences_validPayload_returns200() {
        savedPreferences = new Preferences();
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

        given().
                port(getAlicePort()).
                body(savedPreferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(200).
                body("autoSelectArbitrators", equalTo(savedPreferences.autoSelectArbitrators)).
                body("baseCurrencyNetwork", equalTo(savedPreferences.baseCurrencyNetwork)).
                body("blockChainExplorer", equalTo(savedPreferences.blockChainExplorer)).
                body("cryptoCurrencies", equalTo(savedPreferences.cryptoCurrencies)).
                body("fiatCurrencies", equalTo(savedPreferences.fiatCurrencies)).
                body("ignoredTraders", equalTo(Arrays.asList("jes", "ber:3333"))).
                body("maxPriceDistance", equalTo(savedPreferences.maxPriceDistance.floatValue())).
                body("preferredTradeCurrency", equalTo(savedPreferences.preferredTradeCurrency)).
                body("useCustomWithdrawalTxFee", equalTo(savedPreferences.useCustomWithdrawalTxFee)).
                body("userCountry", equalTo(savedPreferences.userCountry)).
                body("userLanguage", equalTo(savedPreferences.userLanguage)).
                body("withdrawalTxFee", equalTo(savedPreferences.withdrawalTxFee.intValue()))
        ;
    }

    @InSequence(2)
    @Test
    public void getPreferences_always_returns200() {
        given().
                port(getAlicePort()).
//
        when().
                get("/api/v1/preferences").
//
        then().
                statusCode(200).
                body("autoSelectArbitrators", equalTo(savedPreferences.autoSelectArbitrators)).
                body("baseCurrencyNetwork", equalTo(savedPreferences.baseCurrencyNetwork)).
                body("blockChainExplorer", equalTo(savedPreferences.blockChainExplorer)).
                body("cryptoCurrencies", equalTo(savedPreferences.cryptoCurrencies)).
                body("fiatCurrencies", equalTo(savedPreferences.fiatCurrencies)).
                body("ignoredTraders", equalTo(Arrays.asList("jes", "ber:3333"))).
                body("maxPriceDistance", equalTo(savedPreferences.maxPriceDistance.floatValue())).
                body("preferredTradeCurrency", equalTo(savedPreferences.preferredTradeCurrency)).
                body("useCustomWithdrawalTxFee", equalTo(savedPreferences.useCustomWithdrawalTxFee)).
                body("userCountry", equalTo(savedPreferences.userCountry)).
                body("userLanguage", equalTo(savedPreferences.userLanguage)).
                body("withdrawalTxFee", equalTo(savedPreferences.withdrawalTxFee.intValue()))
        ;
    }

    @InSequence(3)
    @Test
    public void setPreferences_invalidBlockChainExplorer_returns422() {
        final Preferences preferences = new Preferences();
        preferences.blockChainExplorer = "abc";

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(422).
                body("errors.size()", equalTo(1)).
                body("errors[0]", equalTo("Unsupported value of blockChainExplorer: abc"))
        ;
        getPreferences_always_returns200();
    }

    @InSequence(3)
    @Test
    public void setPreferences_unsupportedCryptoCurrencyCode_returns422() {
        final Preferences preferences = new Preferences();
        preferences.cryptoCurrencies = Collections.singletonList("BTC");

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(422).
                body("errors.size()", equalTo(1)).
                body("errors[0]", equalTo("Unsupported crypto currency code: BTC"))
        ;
        getPreferences_always_returns200();
    }

    @InSequence(3)
    @Test
    public void setPreferences_nullCryptoCurrencyCode_returns422() {
        final Preferences preferences = new Preferences();
        preferences.cryptoCurrencies = Collections.singletonList(null);

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(422).
                body("errors.size()", equalTo(1)).
                body("errors[0]", equalTo("Unsupported crypto currency code: null"))
        ;
        getPreferences_always_returns200();
    }

    @InSequence(3)
    @Test
    public void setPreferences_unsupportedFiatCurrencyCode_returns422() {
        final Preferences preferences = new Preferences();
        preferences.fiatCurrencies = Collections.singletonList("BTC");

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(422).
                body("errors.size()", equalTo(1)).
                body("errors[0]", equalTo("Unsupported fiat currency code: BTC"))
        ;
        getPreferences_always_returns200();
    }

    @InSequence(3)
    @Test
    public void setPreferences_nullFiatCurrencyCode_returns422() {
        final Preferences preferences = new Preferences();
        preferences.fiatCurrencies = Collections.singletonList(null);

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(422).
                body("errors.size()", equalTo(1)).
                body("errors[0]", equalTo("Unsupported fiat currency code: null"))
        ;
        getPreferences_always_returns200();
    }

    @InSequence(3)
    @Test
    public void setPreferences_nullIgnoredTraders_returns422() {
        final Preferences preferences = new Preferences();
        preferences.ignoredTraders = Collections.singletonList(null);

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(422).
                body("errors.size()", equalTo(1)).
                body("errors[0]", equalTo("ignoredTraders must not contain null elements"))
        ;
        getPreferences_always_returns200();
    }

    @InSequence(3)
    @Test
    public void setPreferences_unsupportedPreferredTradeCurrency_returns422() {
        final Preferences preferences = new Preferences();
        preferences.preferredTradeCurrency = "ABC";

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(422).
                body("errors.size()", equalTo(1)).
                body("errors[0]", equalTo("Unsupported trade currency code: ABC"))
        ;
        getPreferences_always_returns200();
    }

    @InSequence(3)
    @Test
    public void setPreferences_unsupportedUserCountry_returns422() {
        final Preferences preferences = new Preferences();
        preferences.userCountry = "ABC";

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(422).
                body("errors.size()", equalTo(1)).
                body("errors[0]", equalTo("userCountry is not valid country code"))
        ;
        getPreferences_always_returns200();
    }

    @InSequence(3)
    @Test
    public void setPreferences_baseCurrencyNetwork_returns422() {
        final Preferences preferences = new Preferences();
        preferences.baseCurrencyNetwork = "LTC";

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(422).
                body("errors.size()", equalTo(1)).
                body("errors[0]", equalTo("Changing baseCurrencyNetwork is not supported"))
        ;
        getPreferences_always_returns200();
    }

    @InSequence(4)
    @Test
    public void setPreferences_validPartialPayload_returns200() {
        final Preferences preferences = new Preferences();
        preferences.autoSelectArbitrators = true;

        given().
                port(getAlicePort()).
                body(preferences).
                contentType(ContentType.JSON).
//
        when().
                put("/api/v1/preferences").
//
        then().
                statusCode(200).
                body("autoSelectArbitrators", equalTo(preferences.autoSelectArbitrators)).
                body("baseCurrencyNetwork", equalTo(savedPreferences.baseCurrencyNetwork)).
                body("blockChainExplorer", equalTo(savedPreferences.blockChainExplorer)).
                body("cryptoCurrencies", equalTo(savedPreferences.cryptoCurrencies)).
                body("fiatCurrencies", equalTo(savedPreferences.fiatCurrencies)).
                body("ignoredTraders", equalTo(Arrays.asList("jes", "ber:3333"))).
                body("maxPriceDistance", equalTo(savedPreferences.maxPriceDistance.floatValue())).
                body("preferredTradeCurrency", equalTo(savedPreferences.preferredTradeCurrency)).
                body("useCustomWithdrawalTxFee", equalTo(savedPreferences.useCustomWithdrawalTxFee)).
                body("userCountry", equalTo(savedPreferences.userCountry)).
                body("userLanguage", equalTo(savedPreferences.userLanguage)).
                body("withdrawalTxFee", equalTo(savedPreferences.withdrawalTxFee.intValue()))
        ;
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
