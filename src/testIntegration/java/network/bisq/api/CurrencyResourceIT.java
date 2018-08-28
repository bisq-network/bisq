package network.bisq.api;

import network.bisq.api.model.Currency;
import network.bisq.api.model.CurrencyList;
import network.bisq.api.model.Preferences;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@RunWith(Arquillian.class)
public class CurrencyResourceIT {

    @DockerContainer
    private Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, false);

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
    }

    @InSequence(1)
    @Test
    public void getCurrencyList_always_returns200() {
        final CurrencyList currencyList = given().
                port(getAlicePort()).
//
        when().
                        get("/api/v1/currencies").
//
        then().
                        statusCode(200).
                        and().body("total", isA(Integer.class)).
                        and().body("currencies[0].code", isA(String.class)).
                        and().body("currencies[0].name", isA(String.class)).
                        and().body("currencies[0].type", isA(String.class)).
                        and().body("currencies[0].type", isOneOf("crypto", "fiat")).
                        extract().as(CurrencyList.class);
        /**
         * Make sure that currency code is used instead of symbol
         */
        final Optional<Currency> usd = currencyList.currencies.stream().filter(currency -> "USD".equals(currency.code)).findFirst();
        Assert.assertTrue(usd.isPresent());
    }

    @InSequence(1)
    @Test
    public void getPriceFeed_withoutAnyParams_returnsPricesForDefaultCurrencies() {
        final Preferences preferences = given().
                port(getAlicePort()).
//
        when().
                        get("/api/v1/preferences").
//
        then().
                        statusCode(200).
                        extract().as(Preferences.class);
        final List<String> defaultCodes = new ArrayList<>(preferences.cryptoCurrencies);
        defaultCodes.addAll(preferences.fiatCurrencies);
        final HashMap<String, Double> map = given().
                port(getAlicePort()).
//
        when().
                        get("/api/v1/currencies/prices").
//
        then().
                        statusCode(200).
                        and().body("prices.size()", greaterThan(0))
                .extract().path("prices");
        for (String code : map.keySet()) {
            Assert.assertTrue("Response should contain only default currencies", defaultCodes.contains(code));
        }
    }

    @InSequence(1)
    @Test
    public void getPriceFeed_withCurrencyCodesParam_returnsOnlyPricesForRelatedCurrencies() {
        given().
                port(getAlicePort()).
                queryParam("currencyCodes", "PLN,XMR").
//
        when().
                get("/api/v1/currencies/prices").
//
        then().
                statusCode(200).
                and().body("prices.size()", equalTo(2)).
                and().body("prices.PLN", isA(Number.class)).
                and().body("prices.XMR", isA(Number.class))
        ;
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
