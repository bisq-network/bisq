package io.bisq.api;

import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.ContainerBuilder;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@RunWith(Arquillian.class)
public class RegisterArbiterIT {

    @DockerContainer
    Container alice = createApiContainer("alice", "8081->8080", 3333);

    @DockerContainer
    Container arbitrator = createApiContainer("arbitrator", "8082->8080", 3335);

    @DockerContainer(order = 4)
    Container seedNode = createSeedNodeContainer();

    private ContainerBuilder.ContainerOptionsBuilder withRegtestEnv(ContainerBuilder.ContainerOptionsBuilder builder) {
        return builder
                .withEnvironment("USE_LOCALHOST_FOR_P2P", "true")
                .withEnvironment("BASE_CURRENCY_NETWORK", "BTC_REGTEST")
                .withEnvironment("BTC_NODES", "bisq-bitcoin:18332")
                .withEnvironment("SEED_NODES", "bisq-seednode:8000")
                .withEnvironment("LOG_LEVEL", "debug");
    }

    private Container createApiContainer(String nameSuffix, String portBinding, int nodePort)
    {
        return withRegtestEnv(Container.withContainerName("bisq-api-" + nameSuffix).fromImage("bisq-api").withVolume("m2", "/root/.m2").withPortBinding(portBinding))
                .withEnvironment("NODE_PORT", nodePort)
                .withLink("bisq-seednode")
                .build();
    }

    private Container createSeedNodeContainer() {
        return withRegtestEnv(Container.withContainerName("bisq-seednode").fromImage("bisq-seednode").withVolume("m2", "/root/.m2"))
                .withEnvironment("MY_ADDRESS", "bisq-seednode:8000")
                .build();
    }

    @Test
    public void registerArbitrator() throws InterruptedException {
//        TODO it would be nice to expose endpoint that would respond with 200
        /* Wait for p2p network to be initialized (cannot register arbiter before that)*/
        final int P2P_INIT_DELAY = 10000;
        Thread.sleep(P2P_INIT_DELAY);
        given().
                port(arbitrator.getBindPort(8080)).
                when().
                formParam("languageCodes", "en,de").
                post("/api/v1/arbitrator_register").
                then().statusCode(200);
        /* Wait for arbiter registration message to be broadcast across peers*/
        final int P2P_MSG_RELAY_DELAY = 1000;
        Thread.sleep(P2P_MSG_RELAY_DELAY);
        given().
                port(alice.getBindPort(8080)).
                when().
                get("/api/v1/arbitrators").
                then().statusCode(200).
                and().body(containsString(":3335"));
    }


}
