package network.bisq.httpapi.service.v1;

import lombok.extern.slf4j.Slf4j;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import network.bisq.httpapi.BisqProxy;
import network.bisq.httpapi.model.ClosedTradableList;

@Slf4j
@Api(value = "closed-tradables", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class ClosedTradableResource {

    private final BisqProxy bisqProxy;

    public ClosedTradableResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("List portfolio history")
    @GET
    public ClosedTradableList listClosedTrades() {
        final ClosedTradableList list = new ClosedTradableList();
        list.closedTradables = bisqProxy.getClosedTradableList();
        list.total = list.closedTradables.size();
        return list;
    }

}
