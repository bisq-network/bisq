package bisq.httpapi.service.endpoint;

import bisq.httpapi.facade.PriceFeedFacade;
import bisq.httpapi.model.CurrencyList;
import bisq.httpapi.model.PriceFeed;

import bisq.common.UserThread;

import javax.inject.Inject;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;

@Api(value = "currencies", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class CurrencyEndpoint {

    private final PriceFeedFacade priceFeedFacade;

    @Inject
    public CurrencyEndpoint(PriceFeedFacade priceFeedFacade) {
        this.priceFeedFacade = priceFeedFacade;
    }

    @ApiOperation(value = "List available currencies", response = CurrencyList.class)
    @GET
    public void getCurrencyList(@Suspended final AsyncResponse asyncResponse) {
        UserThread.execute(() -> {
            try {
                asyncResponse.resume(MarketEndpoint.getCurrencyList());
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Get market prices", notes = "If currencyCodes is not provided then currencies from preferences are used.", response = PriceFeed.class)
    @GET
    @Path("/prices")
    public void getPriceFeed(@Suspended final AsyncResponse asyncResponse, @QueryParam("currencyCodes") String currencyCodes) {
        UserThread.execute(() -> {
            try {
                final String[] codes;
                if (null == currencyCodes || 0 == currencyCodes.length())
                    codes = new String[0];
                else
                    codes = currencyCodes.split("\\s*,\\s*");
                asyncResponse.resume(priceFeedFacade.getPriceFeed(codes));
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }
}
