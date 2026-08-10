/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.restapi.endpoints;

import bisq.core.dao.governance.bond.role.BondedRoleRegistration;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;

import bisq.common.util.Base64;
import bisq.common.util.Hex;

import lombok.extern.slf4j.Slf4j;

import bisq.restapi.RestApi;
import bisq.restapi.RestApiMain;
import bisq.restapi.dto.BondedRoleVerificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint for getting the bonded role verification for the given parameters.
 * Used for bonded roles in Bisq 2.
 * <a href="http://localhost:8082/api/v1/bonded-role-verification">Bonded role verification API</a>
 */
@Slf4j
@Path("/bonded-role-verification")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Bonded role API")
public class BondedRoleVerificationApi {
    private final RestApi restApi;
    private final BondedRolesRepository bondedRolesRepository;

    public BondedRoleVerificationApi(@Context Application application) {
        restApi = ((RestApiMain) application).getRestApi();
        bondedRolesRepository = restApi.getBondedRolesRepository();
    }

    @Operation(description = "Request the verification of a bonded role with the provided parameters")
    @ApiResponse(responseCode = "200", description = "A BondedRoleVerification result object",
            content = {@Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(allOf = BondedRoleVerificationDto.class))}
    )
    @ApiResponse(responseCode = "503", description = "DAO state is not ready and in sync",
            content = {@Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(allOf = BondedRoleVerificationDto.class))}
    )
    @GET
    @Path("get-bonded-role-verification/{protocol-version}/{bond-user-name}/{role-type}/{proposal-tx-id}/{lockup-tx-id}/{profile-id}/{signature}")
    public BondedRoleVerificationDto getBondedRoleVerification(@PathParam("protocol-version") String protocolVersion,
                                                               @PathParam("bond-user-name") String bondUserName,
                                                               @PathParam("role-type") String roleType,
                                                               @PathParam("proposal-tx-id") String proposalTxId,
                                                               @PathParam("lockup-tx-id") String lockupTxId,
                                                               @PathParam("profile-id") String profileId,
                                                               @PathParam("signature") String signature) {
        checkDaoReadyAndInSync();
        log.info("Received request for verifying a bonded role. bondUserName={}, roleType={}, profileId={}",
                bondUserName, roleType, profileId);

        // Taken as a String so that a malformed version is answered with the same structured result as any other
        // invalid parameter. A path parameter which cannot be converted would otherwise be answered with a plain 404,
        // which a caller cannot tell apart from an unknown route.
        int version;
        try {
            version = Integer.parseInt(protocolVersion);
        } catch (NumberFormatException e) {
            return new BondedRoleVerificationDto("Bonded role invalid. Invalid protocol version.");
        }

        String signatureBase64;
        try {
            signatureBase64 = Base64.encode(Hex.decode(signature));
        } catch (IllegalArgumentException e) {
            return new BondedRoleVerificationDto("Bonded role invalid. Invalid signature encoding.");
        }

        try {
            bondedRolesRepository.verifyBondedRole(new BondedRoleRegistration(
                    version,
                    bondUserName,
                    roleType,
                    proposalTxId,
                    lockupTxId,
                    profileId,
                    signatureBase64));
            return new BondedRoleVerificationDto();
        } catch (IllegalArgumentException e) {
            return new BondedRoleVerificationDto("Bonded role invalid. " + e.getMessage());
        }
    }

    @GET
    @Path("get-bonded-role-verification/{bond-user-name}/{role-type}/{profile-id}/{signature}")
    public Response rejectLegacyBondedRoleVerification() {
        return Response.status(426)
                .type(MediaType.APPLICATION_JSON)
                .entity(new BondedRoleVerificationDto(
                        "Bonded role invalid. The unbound registration protocol is no longer supported."))
                .build();
    }

    private void checkDaoReadyAndInSync() {
        try {
            restApi.checkDaoReadyAndInSync();
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new BondedRoleVerificationDto("DAO state is not ready and in sync."))
                    .build());
        }
    }
}
