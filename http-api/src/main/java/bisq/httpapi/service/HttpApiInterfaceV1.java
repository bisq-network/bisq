package bisq.httpapi.service;

import javax.inject.Inject;



import bisq.httpapi.service.endpoint.VersionEndpoint;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Path;

@OpenAPIDefinition(
        info = @Info(version = "0.0.1", title = "Bisq HTTP API"),
        tags = {
                @Tag(name = "version")
        }
)
@Path("/api/v1")
public class HttpApiInterfaceV1 {
    private final VersionEndpoint versionEndpoint;

    @Inject
    public HttpApiInterfaceV1(VersionEndpoint versionEndpoint) {
        this.versionEndpoint = versionEndpoint;
    }

    @Path("version")
    public VersionEndpoint getVersionEndpoint() {
        return versionEndpoint;
    }

}
