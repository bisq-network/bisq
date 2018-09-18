package bisq.httpapi;

import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;



import io.restassured.response.ValidatableResponse;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;

@RunWith(Arquillian.class)
public class ArbitratorEndpointIT {

    @DockerContainer
    Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, true, false);

    @DockerContainer
    Container arbitrator = ContainerFactory.createApiContainer("arbitrator", "8082->8080", 3335, true, false);

    @DockerContainer(order = 4)
    Container seedNode = ContainerFactory.createSeedNodeContainer();


    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
    }

    @InSequence(1)
    @Test
    public void registerArbitrator() throws InterruptedException {
        final int alicePort = getAlicePort();
        final int arbitratorPort = getArbitratorPort();
        given().
                port(alicePort).
//
        when().
                get("/api/v1/arbitrators").
//
        then().
                statusCode(200).
                and().body("arbitrators.size()", equalTo(0)).
                and().body("total", equalTo(0));

        ApiTestHelper.registerArbitrator(arbitratorPort);

        given().
                port(alicePort).
//
        when().
                get("/api/v1/arbitrators").
//
        then()
                .statusCode(200).
                and().body("arbitrators.size()", equalTo(1)).
                and().body("total", equalTo(1)).
                and().body(containsString(":3335"));
        assertNumberOfAcceptedArbitrators(alicePort, 1);
    }

    /**
     * Deselect test goes before select test because by default arbitrators are auto selected when registered
     */
    @InSequence(3)
    @Test
    public void deselectArbitrator() {
        final int alicePort = getAlicePort();
        final String arbitratorAddress = getArbitratorAddress(alicePort);

        assertNumberOfAcceptedArbitrators(alicePort, 1);

        given().
                port(alicePort).
                pathParam("address", arbitratorAddress).
//
        when().
                post("/api/v1/arbitrators/{address}/deselect").
//
        then().
                statusCode(200).
                and().body("arbitrators.size()", equalTo(0)).
                and().body("total", equalTo(0));

        assertNumberOfAcceptedArbitrators(alicePort, 0);
    }

    @InSequence(4)
    @Test
    public void selectArbitrator() {
        final int alicePort = getAlicePort();
        final String arbitratorAddress = getArbitratorAddress(alicePort);

        assertNumberOfAcceptedArbitrators(alicePort, 0);

        given().
                port(alicePort).
                pathParam("address", arbitratorAddress).
//
        when().
                post("/api/v1/arbitrators/{address}/select").
//
        then().
                statusCode(200).
                and().body("arbitrators.size()", equalTo(1)).
                and().body("total", equalTo(1));

        assertNumberOfAcceptedArbitrators(alicePort, 1);
    }

    private String getArbitratorAddress(int alicePort) {
        return given().
                port(alicePort).
                when().
                get("/api/v1/arbitrators").
                body().jsonPath().get("arbitrators[0].address");
    }

    private ValidatableResponse assertNumberOfAcceptedArbitrators(int apiPort, int expectedArbitratorsCount) {
        return given().
                port(apiPort).
                queryParam("acceptedOnly", "true").
//
        when().
                        get("/api/v1/arbitrators").
//
        then().
                        statusCode(200).
                        and().body("arbitrators.size()", equalTo(expectedArbitratorsCount)).
                        and().body("total", equalTo(expectedArbitratorsCount));
    }

    private int getArbitratorPort() {
        return arbitrator.getBindPort(8080);
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
