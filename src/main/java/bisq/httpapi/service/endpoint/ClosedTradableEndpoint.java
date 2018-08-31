package bisq.httpapi.service.endpoint;

import bisq.httpapi.facade.ClosedTradableFacade;
import bisq.httpapi.model.ClosedTradableList;

import bisq.common.UserThread;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
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

    @ApiOperation(value = "List portfolio history", response = ClosedTradableList.class)
    @GET
    public void listClosedTrades(@Suspended final AsyncResponse asyncResponse) {
        UserThread.execute(() -> {
            try {
                final ClosedTradableList list = new ClosedTradableList();
                list.closedTradables = closedTradableFacade.getClosedTradableList();
                list.total = list.closedTradables.size();
                asyncResponse.resume(list);
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

}
