package bisq.api.http.service;

import bisq.api.http.service.endpoint.UserEndpoint;
import bisq.api.http.service.endpoint.VersionEndpoint;

import javax.inject.Inject;



import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Path;

@SecurityScheme(
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        name = "authorization",
        paramName = "authorization"
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
