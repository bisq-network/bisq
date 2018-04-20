package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.CurrencyList;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "currencies", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class CurrencyResource {

    private final BisqProxy bisqProxy;

    public CurrencyResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("List available currencies")
    @GET
    public CurrencyList getCurrencyList() {
        return bisqProxy.getCurrencyList();
    }
}
