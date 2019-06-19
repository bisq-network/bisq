/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.api.http;

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
