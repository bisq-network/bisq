package network.bisq.api.service.v1;

import network.bisq.api.BisqProxy;
import network.bisq.api.model.CurrencyList;
import network.bisq.api.model.PriceFeed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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

    @ApiOperation(value = "Get market prices", notes = "If currencyCodes is not provided then currencies from preferences are used.")
    @GET
    @Path("/prices")
    public PriceFeed getPriceFeed(@QueryParam("currencyCodes") String currencyCodes) {
        final String[] codes;
        if (null == currencyCodes || 0 == currencyCodes.length())
            codes = new String[0];
        else
            codes = currencyCodes.split("\\s*,\\s*");
        return bisqProxy.getPriceFeed(codes);
    }
}
