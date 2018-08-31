package bisq.httpapi.service.endpoint;

import bisq.httpapi.facade.PaymentAccountFacade;
import bisq.httpapi.model.PaymentAccountList;
import bisq.httpapi.model.payment.PaymentAccount;
import bisq.httpapi.model.payment.PaymentAccountHelper;

import javax.inject.Inject;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "payment-accounts", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class PaymentAccountEndpoint {

    private final PaymentAccountFacade paymentAccountFacade;

    @Inject
    public PaymentAccountEndpoint(PaymentAccountFacade paymentAccountFacade) {
        this.paymentAccountFacade = paymentAccountFacade;
    }

    @ApiOperation("Remove payment account")
    @DELETE
    @Path("/{id}")
    public void removeById(@PathParam("id") String id) {
        paymentAccountFacade.removePaymentAccount(id);
    }

    @ApiOperation(value = "Create payment account", notes = "Inspect models section at the bottom of the page for valid PaymentAccount sub-types schemas")
    @POST
    public PaymentAccount create(@Valid PaymentAccount account) {
        final bisq.core.payment.PaymentAccount paymentAccount = PaymentAccountHelper.toBusinessModel(account);
        return PaymentAccountHelper.toRestModel(paymentAccountFacade.addPaymentAccount(paymentAccount));
    }

    @ApiOperation("Get existing payment accounts")
    @GET
    public PaymentAccountList find() {
        return paymentAccountFacade.getAccountList();
    }
}
