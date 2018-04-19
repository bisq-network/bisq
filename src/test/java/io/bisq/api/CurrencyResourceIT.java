package io.bisq.api;

import io.bisq.api.model.Currency;
import io.bisq.api.model.CurrencyList;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.isOneOf;

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


    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
