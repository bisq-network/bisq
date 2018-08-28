package network.bisq.api;

import bisq.core.offer.OfferPayload;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.junit.runner.RunWith;

import static network.bisq.api.ApiTestHelper.isIntegerOrLong;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@RunWith(Arquillian.class)
public class ClosableTradableResourceIT {

    @DockerContainer
    private Container alice;

    @DockerContainer
    private Container bob;

    @DockerContainer
    private Container arbitrator;

    @SuppressWarnings("unused")
    @DockerContainer(order = 4)
    private Container seedNode;

    @DockerContainer
    private Container bitcoin;

    private TradeResourceIT tradeResourceIT = new TradeResourceIT();

    {
        alice = tradeResourceIT.alice;
        bob = tradeResourceIT.bob;
        arbitrator = tradeResourceIT.arbitrator;
        seedNode = tradeResourceIT.seedNode;
        bitcoin = tradeResourceIT.bitcoin;
    }

    @InSequence
    @Test
    public void setupOfferAndTrade() throws Exception {
        tradeResourceIT.setupTrade();
        tradeResourceIT.generateBitcoinBlock();
        tradeResourceIT.paymentStarted_tradeExists_returns200();
        tradeResourceIT.paymentReceived_tradeExists_returns200();
        tradeResourceIT.moveFundsToBisqWallet_tradeExists_returns200();
        tradeResourceIT.offerResourceIT.createOffer_validPayloadAndHasFunds_returnsOffer();
        tradeResourceIT.offerResourceIT.cancelOffer_ownExistingOffer_returns200();
    }

    @InSequence(1)
    @Test
    public void listClosedTrades_always_returns200() {
        given().
                port(getAlicePort()).
//
        when().
                get("/api/v1/closed-tradables").
//
        then().
                statusCode(200).
                and().body("total", equalTo(2)).
                // cancelled offer
                and().body("closedTradables[0].amount", equalTo(null)).
                and().body("closedTradables[0].currencyCode", isA(String.class)).
                and().body("closedTradables[0].date", isIntegerOrLong()).
                and().body("closedTradables[0].direction", isOneOf(OfferPayload.Direction.BUY.name(), OfferPayload.Direction.SELL.name())).
                and().body("closedTradables[0].id", isA(String.class)).
                and().body("closedTradables[0].price", isIntegerOrLong()).
                and().body("closedTradables[0].status", equalTo("Canceled")).
                and().body("closedTradables[0].volume", equalTo(null)).
                // completed trade
                and().body("closedTradables[1].amount", isIntegerOrLong()).
                and().body("closedTradables[1].currencyCode", isA(String.class)).
                and().body("closedTradables[1].date", isIntegerOrLong()).
                and().body("closedTradables[1].direction", isOneOf(OfferPayload.Direction.BUY.name(), OfferPayload.Direction.SELL.name())).
                and().body("closedTradables[1].id", isA(String.class)).
                and().body("closedTradables[1].price", isIntegerOrLong()).
                and().body("closedTradables[1].status", equalTo("Completed")).
                and().body("closedTradables[1].volume", isIntegerOrLong())
        ;
    }


    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
