package io.bisq.api;

import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToIgnoringWhiteSpace;

@RunWith(Arquillian.class)
public class SampleIT {

    @DockerContainer
    Container alice = createApiContainer("alice", "8081->8080");

    @DockerContainer
    Container bob = createApiContainer("bob", "8080->8080");

    private Container createApiContainer(String nameSuffix, String portBinding)
    {
        return Container.withContainerName("bisq-api-" + nameSuffix).fromImage("bisq-api").withVolume("m2", "/root/.m2").withPortBinding(portBinding).build();
    }

    @Test
    public void getSwagger() throws InterruptedException
    {
        given().
            port(alice.getBindPort(8080)).
            when().
            get("/swagger").
            then().statusCode(200).and().body(containsString("<title>Swagger UI</title>"));
    }

    @Test
    public void getAccountList() throws InterruptedException
    {
        given().
            port(bob.getBindPort(8080)).
            when().
                get("/api/v1/payment-accounts").
                then().
                statusCode(200).
                and().body(equalToIgnoringWhiteSpace("{\"paymentAccounts\":[]}"));
    }
}
