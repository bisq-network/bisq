package bisq.httpapi.service.endpoint;

import bisq.common.UserThread;

import bisq.httpapi.facade.PaymentAccountFacade;
import bisq.httpapi.model.PaymentAccountList;
import bisq.httpapi.model.payment.PaymentAccount;
import bisq.httpapi.model.payment.PaymentAccountHelper;
import bisq.httpapi.service.ExperimentalFeature;

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
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "payment-accounts", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class PaymentAccountEndpoint {

    private final ExperimentalFeature experimentalFeature;
    private final PaymentAccountFacade paymentAccountFacade;

    @Inject
    public PaymentAccountEndpoint(ExperimentalFeature experimentalFeature, PaymentAccountFacade paymentAccountFacade) {
        this.experimentalFeature = experimentalFeature;
        this.paymentAccountFacade = paymentAccountFacade;
    }

    @ApiOperation(value = "Remove payment account", notes = ExperimentalFeature.NOTE)
    @DELETE
    @Path("/{id}")
    public void removeById(@Suspended final AsyncResponse asyncResponse, @PathParam("id") String id) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                paymentAccountFacade.removePaymentAccount(id);
                asyncResponse.resume(Response.status(Response.Status.NO_CONTENT).build());
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Create payment account", notes = ExperimentalFeature.NOTE + "\nInspect models section at the bottom of the page for valid PaymentAccount sub-types schemas", response = PaymentAccount.class)
    @POST
    public void create(@Suspended final AsyncResponse asyncResponse, @Valid PaymentAccount account) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                final bisq.core.payment.PaymentAccount paymentAccount = PaymentAccountHelper.toBusinessModel(account);
                final PaymentAccount result = PaymentAccountHelper.toRestModel(paymentAccountFacade.addPaymentAccount(paymentAccount));
                asyncResponse.resume(result);
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Get existing payment accounts", response = PaymentAccountList.class, notes = ExperimentalFeature.NOTE)
    @GET
    public void find(@Suspended final AsyncResponse asyncResponse) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                asyncResponse.resume(paymentAccountFacade.getAccountList());
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }
}
