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

package bisq.daoNode;

import lombok.extern.slf4j.Slf4j;



import io.swagger.v3.core.util.Json;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Slf4j
@Path("openapi.json")
@Produces(MediaType.APPLICATION_JSON)
@Hidden
public class SwaggerResolution {
    private static String swaggerJson;

    @GET
    public String swagIt(@Context Application application) {
        if (swaggerJson == null) {
            try {
                OpenAPI api = new OpenAPI();
                Info info = new Info()
                        .title("Bisq DAO node REST API")
                        .description("This is the rest API description for the Bisq DAO node, For more Information about Bisq, see https://bisq.network")
                        .license(new License()
                                .name("GNU Affero General Public License")
                                .url("https://github.com/bisq-network/bisq2/blob/main/LICENSE"));

                api.info(info).addServersItem(new Server().url(DaoNodeRestApiApplication.BASE_URL));
                SwaggerConfiguration configuration = new SwaggerConfiguration().openAPI(api);
                Reader reader = new Reader(configuration);
                OpenAPI openAPI = reader.read(application.getClasses());
                swaggerJson = Json.pretty(openAPI);
            } catch (RuntimeException e) {
                log.error(e.getMessage(), e);
                throw e;
            }
        }

        return swaggerJson;
    }
}
