package network.bisq.api.service.v1;

import network.bisq.api.BisqProxy;
import network.bisq.api.model.ClosedTradableList;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
