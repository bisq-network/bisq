package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.CurrencyList;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api("currencies")
@Produces(MediaType.APPLICATION_JSON)
public class CurrencyResource {

    private final BisqProxy bisqProxy;

    public CurrencyResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("List available currencies")
    @GET
    @Path("/")
    public CurrencyList find() {
        return bisqProxy.getCurrencyList();
    }
}
