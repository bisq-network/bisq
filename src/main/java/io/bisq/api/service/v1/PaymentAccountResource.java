package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.AccountToCreate;
import io.bisq.api.model.PaymentAccountList;
import io.bisq.api.model.PaymentAccount;
import io.swagger.annotations.Api;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api("payment-accounts")
@Produces(MediaType.APPLICATION_JSON)
public class PaymentAccountResource {

    private final BisqProxy bisqProxy;

    public PaymentAccountResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @GET
    @Path("/{id}")
    public PaymentAccount getById(@PathParam("id") String id) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @DELETE
    @Path("/{id}")
    public void removeById(@PathParam("id") String id) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @POST
    @Path("/")
    public PaymentAccount create(@Valid AccountToCreate account) {
        return bisqProxy.addPaymentAccount(account);
    }

    @GET
    @Path("/")
    public PaymentAccountList find() {
        return bisqProxy.getAccountList();
    }
}
