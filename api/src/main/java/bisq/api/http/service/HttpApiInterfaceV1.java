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

package bisq.api.http.service;

import bisq.api.http.service.endpoint.UserEndpoint;
import bisq.api.http.service.endpoint.VersionEndpoint;

import javax.inject.Inject;



import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Path;

@SecurityScheme(
        scheme = "basic",
        type = SecuritySchemeType.HTTP,
        name = "authorization"
)
@OpenAPIDefinition(
        info = @Info(version = "0.0.1", title = "Bisq HTTP API"),
        security = @SecurityRequirement(name = "authorization"),
        tags = {
                @Tag(name = "user"),
                @Tag(name = "version")
        }
)
@Path("/api/v1")
public class HttpApiInterfaceV1 {
    private final UserEndpoint userEndpoint;
    private final VersionEndpoint versionEndpoint;

    @Inject
    public HttpApiInterfaceV1(UserEndpoint userEndpoint, VersionEndpoint versionEndpoint) {
        this.userEndpoint = userEndpoint;
        this.versionEndpoint = versionEndpoint;
    }

    @Path("user")
    public UserEndpoint getUserEndpoint() {
        return userEndpoint;
    }

    @Path("version")
    public VersionEndpoint getVersionEndpoint() {
        return versionEndpoint;
    }
}
