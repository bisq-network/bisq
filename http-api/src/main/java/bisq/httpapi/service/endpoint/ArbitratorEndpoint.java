package bisq.httpapi.service.endpoint;

import bisq.common.UserThread;

import bisq.httpapi.facade.ArbitratorFacade;
import bisq.httpapi.model.Arbitrator;
import bisq.httpapi.model.ArbitratorList;
import bisq.httpapi.model.ArbitratorRegistration;
import bisq.httpapi.service.ExperimentalFeature;

import javax.inject.Inject;

import java.util.Collection;
import java.util.stream.Collectors;



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
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.hibernate.validator.constraints.NotBlank;

@Api(value = "arbitrators", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class ArbitratorEndpoint {

    private final ArbitratorFacade arbitratorFacade;
    private final ExperimentalFeature experimentalFeature;

    @Inject
    public ArbitratorEndpoint(ArbitratorFacade arbitratorFacade, ExperimentalFeature experimentalFeature) {
        this.arbitratorFacade = arbitratorFacade;
        this.experimentalFeature = experimentalFeature;
    }

    @ApiOperation(value = "Unregister yourself as arbitrator", notes = ExperimentalFeature.NOTE)
    @DELETE
    public void unregister() {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @ApiOperation(value = "Register yourself as arbitrator", notes = ExperimentalFeature.NOTE)
    @POST
    public void register(@Suspended final AsyncResponse asyncResponse, @Valid ArbitratorRegistration data) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                arbitratorFacade.registerArbitrator(data.languageCodes);
                asyncResponse.resume(Response.noContent().build());
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Find available arbitrators", response = ArbitratorList.class, notes = ExperimentalFeature.NOTE)
    @GET
    public void find(@Suspended final AsyncResponse asyncResponse, @QueryParam("acceptedOnly") boolean acceptedOnly) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                final ArbitratorList arbitratorList = toRestModel(arbitratorFacade.getArbitrators(acceptedOnly));
                asyncResponse.resume(arbitratorList);
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Select arbitrator", response = ArbitratorList.class, notes = ExperimentalFeature.NOTE)
    @POST
    @Path("/{address}/select")
    public void selectArbitrator(@Suspended final AsyncResponse asyncResponse, @NotBlank @PathParam("address") String address) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                final ArbitratorList arbitratorList = toRestModel(arbitratorFacade.selectArbitrator(address));
                asyncResponse.resume(arbitratorList);
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Deselect arbitrator", response = ArbitratorList.class, notes = ExperimentalFeature.NOTE)
    @POST
    @Path("/{address}/deselect")
    public void deselectArbitrator(@Suspended final AsyncResponse asyncResponse, @NotBlank @PathParam("address") String address) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                final ArbitratorList arbitratorList = toRestModel(arbitratorFacade.deselectArbitrator(address));
                asyncResponse.resume(arbitratorList);
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    private static ArbitratorList toRestModel(Collection<bisq.core.arbitration.Arbitrator> businessModelList) {
        final ArbitratorList arbitratorList = new ArbitratorList();
        arbitratorList.arbitrators = businessModelList
                .stream()
                .map(arbitrator -> new Arbitrator(arbitrator.getNodeAddress().getFullAddress()))
                .collect(Collectors.toList());
        arbitratorList.total = arbitratorList.arbitrators.size();
        return arbitratorList;
    }
}
