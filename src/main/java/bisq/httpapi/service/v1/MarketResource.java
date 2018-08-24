package bisq.httpapi.service.v1;

import javax.inject.Inject;



import bisq.httpapi.BisqProxy;
import bisq.httpapi.model.MarketList;
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

    @Inject
    public MarketResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("List markets")
    @GET
    public MarketList find() {
        return bisqProxy.getMarketList();
    }
}
