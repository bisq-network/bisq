package network.bisq.httpapi.service.v1;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import network.bisq.httpapi.BisqProxy;
import network.bisq.httpapi.model.MarketList;

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
