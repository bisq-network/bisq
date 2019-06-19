package bisq.api.http.service.endpoint;

import bisq.api.http.facade.PaymentAccountFacade;
import bisq.api.http.model.PaymentAccountList;
import bisq.api.http.model.payment.PaymentAccount;
import bisq.api.http.model.payment.PaymentAccountHelper;
import bisq.api.http.service.ExperimentalFeature;

import bisq.common.UserThread;

import javax.inject.Inject;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
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

@Tag(name = "payment-accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentAccountEndpoint {

    private final ExperimentalFeature experimentalFeature;
    private final PaymentAccountFacade paymentAccountFacade;

    @Inject
    public PaymentAccountEndpoint(ExperimentalFeature experimentalFeature, PaymentAccountFacade paymentAccountFacade) {
        this.experimentalFeature = experimentalFeature;
        this.paymentAccountFacade = paymentAccountFacade;
    }

    @Operation(summary = "Remove payment account", description = ExperimentalFeature.NOTE)
    @DELETE
    @Path("/{id}")
    public void removeById(@Suspended AsyncResponse asyncResponse, @PathParam("id") String id) {
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

    @Operation(summary = "Create payment account", description = ExperimentalFeature.NOTE + "\nInspect models section at the bottom of the page for valid PaymentAccount sub-types schemas", responses = @ApiResponse(content = @Content(schema = @Schema(implementation = PaymentAccount.class))))
    @POST
    public void create(@Suspended AsyncResponse asyncResponse, @Valid PaymentAccount account) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                bisq.core.payment.PaymentAccount paymentAccount = PaymentAccountHelper.toBusinessModel(account);
                PaymentAccount result = PaymentAccountHelper.toRestModel(paymentAccountFacade.addPaymentAccount(paymentAccount));
                asyncResponse.resume(result);
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @Operation(summary = "Get existing payment accounts", responses = @ApiResponse(content = @Content(schema = @Schema(implementation = PaymentAccountList.class))))
    @GET
    public void find(@Suspended AsyncResponse asyncResponse) {
        UserThread.execute(() -> {
            try {
                asyncResponse.resume(paymentAccountFacade.getAccountList());
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }
}
