package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.Info;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Api("info")
@Produces(MediaType.APPLICATION_JSON)
public class InfoResource {

    private final BisqProxy bisqProxy;

    public InfoResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation(value = "Get info")
    @GET
    @Path("/")
    public Info getInfo() {
        return bisqProxy.getInfo();
    }
}
