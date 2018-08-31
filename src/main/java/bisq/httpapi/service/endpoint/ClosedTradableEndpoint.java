package bisq.httpapi.service.endpoint;

import bisq.httpapi.facade.ClosedTradableFacade;
import bisq.httpapi.model.ClosedTradableList;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Slf4j
@Api(value = "closed-tradables", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class ClosedTradableEndpoint {

    private final ClosedTradableFacade closedTradableFacade;

    @Inject
    public ClosedTradableEndpoint(ClosedTradableFacade closedTradableFacade) {
        this.closedTradableFacade = closedTradableFacade;
    }

    @ApiOperation("List portfolio history")
    @GET
    public ClosedTradableList listClosedTrades() {
        final ClosedTradableList list = new ClosedTradableList();
        list.closedTradables = closedTradableFacade.getClosedTradableList();
        list.total = list.closedTradables.size();
        return list;
    }

}
