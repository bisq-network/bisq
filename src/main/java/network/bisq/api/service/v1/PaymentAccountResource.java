package network.bisq.api.service.v1;

import network.bisq.api.BisqProxy;
import network.bisq.api.model.PaymentAccountList;
import network.bisq.api.model.payment.PaymentAccount;
import network.bisq.api.model.payment.PaymentAccountHelper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api(value = "payment-accounts", authorizations = @Authorization(value = "accessToken"))
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

    @ApiOperation(value = "Create payment account", notes = "Inspect models section at the bottom of the page for valid PaymentAccount sub-types schemas")
    @POST
    public PaymentAccount create(@Valid PaymentAccount account) {
        final bisq.core.payment.PaymentAccount paymentAccount = PaymentAccountHelper.toBusinessModel(account);
        return PaymentAccountHelper.toRestModel(bisqProxy.addPaymentAccount(paymentAccount));
    }

    @ApiOperation("Get existing payment accounts")
    @GET
    public PaymentAccountList find() {
        return bisqProxy.getAccountList();
    }
}
