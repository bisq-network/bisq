package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.MarketList;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "markets", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class MarketResource {

    private final BisqProxy bisqProxy;

    public MarketResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("List markets")
    @GET
    public MarketList find() {
        return bisqProxy.getMarketList();
    }
}
