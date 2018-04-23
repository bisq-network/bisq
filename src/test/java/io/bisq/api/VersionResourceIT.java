package io.bisq.api;

import bisq.common.app.Version;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;

@RunWith(Arquillian.class)
public class VersionResourceIT {

    @DockerContainer
    private Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, false);

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
    }

    @InSequence(1)
    @Test
    public void getVersionDetails_always_returns200() throws InterruptedException {
        final int alicePort = getAlicePort();

        given().
                port(alicePort).
//
        when().
                get("/api/v1/version").
//
        then().
                statusCode(200).
                and().body("application", equalTo(Version.VERSION)).
                and().body("network", equalTo(Version.P2P_NETWORK_VERSION)).
                and().body("p2PMessage", isA(Integer.class)).
                and().body("localDB", equalTo(Version.LOCAL_DB_VERSION)).
                and().body("tradeProtocol", equalTo(Version.TRADE_PROTOCOL_VERSION))
        ;
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
