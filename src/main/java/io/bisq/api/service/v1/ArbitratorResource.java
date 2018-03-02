package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.Arbitrator;
import io.bisq.api.model.ArbitratorRegistration;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.stream.Collectors;

@Api("arbitrators")
@Produces(MediaType.APPLICATION_JSON)
public class ArbitratorResource {

    private final BisqProxy bisqProxy;

    public ArbitratorResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("Unregister yourself as arbitrator")
    @DELETE
    public void unregister() {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @ApiOperation("Register yourself as arbitrator")
    @POST
    public void register(ArbitratorRegistration data) {
        bisqProxy.registerArbitrator(data.languageCodes);
    }

    @ApiOperation("Find arbitrators")
    @GET
    public Collection<Arbitrator> find() {
        return bisqProxy.getArbitrators().stream().map(arbitrator -> new Arbitrator(arbitrator.getNodeAddress().getFullAddress())).collect(Collectors.toList());
    }
}
