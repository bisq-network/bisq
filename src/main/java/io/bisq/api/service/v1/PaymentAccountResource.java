package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.PaymentAccount;
import io.bisq.api.model.PaymentAccountList;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api("payment-accounts")
@Produces(MediaType.APPLICATION_JSON)
public class PaymentAccountResource {

    private final BisqProxy bisqProxy;

    public PaymentAccountResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("Remove payment account")
    @DELETE
    @Path("/{id}")
    public void removeById(@PathParam("id") String id) {
        bisqProxy.removePaymentAccount(id);
    }

    @ApiOperation("Create payment account")
    @POST
    @Path("/")
    public PaymentAccount create(@Valid PaymentAccount account) {
        return bisqProxy.addPaymentAccount(account);
    }

    @ApiOperation("Get existing payment accounts")
    @GET
    @Path("/")
    public PaymentAccountList find() {
        return bisqProxy.getAccountList();
    }
}
