package bisq.httpapi;

import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;



import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;

@RunWith(Arquillian.class)
public class SwaggerIT {

    @DockerContainer
    private Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, false);

    @InSequence(1)
    @Test
    public void getDocs_always_returns200() {
        int alicePort = getAlicePort();

        given().
                port(alicePort).
//
        when().
                get("/docs").
//
        then().
                statusCode(200).
                and().body(containsString("Swagger UI"))
        ;
    }

    @InSequence(1)
    @Test
    public void getOpenApiJson_always_returns200() {
        int alicePort = getAlicePort();

        given().
                port(alicePort).
//
        when().
                get("/openapi.json").
//
        then().
                statusCode(200).
                and().body("info.title", equalTo("Bisq HTTP API"))
        ;
    }

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }

}
