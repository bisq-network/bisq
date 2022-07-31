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

package bisq.daonode.endpoints;

import bisq.core.account.witness.AccountAgeWitness;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;



import bisq.daonode.DaoNodeRestApiApplication;
import bisq.daonode.ServiceNode;
import bisq.daonode.dto.ProofOfBurnDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

/**
 * Endpoint for getting the account age date from a given hash as hex string.
 * Used for reputation system in Bisq 2.
 * <a href="http://localhost:8082/api/v1/account-age/get-date/dd75a7175c7c83fe9a4729e36b85f5fbc44e29ae">Request with hash</a>
 */
@Slf4j
@Path("/account-age")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Account age API")
public class AccountAgeApi {
    private static final String DESC_HASH = "The hash of the account age witness as hex string";
    private final ServiceNode serviceNode;

    public AccountAgeApi(@Context Application application) {
        serviceNode = ((DaoNodeRestApiApplication) application).getServiceNode();
    }

    @Operation(description = "Request the account age date")
    @ApiResponse(responseCode = "200", description = "The account age date",
            content = {@Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(allOf = ProofOfBurnDto.class))}
    )
    @GET
    @Path("get-date/{hash}")
    public Long getDate(@Parameter(description = DESC_HASH)
                        @PathParam("hash")
                        String hash) {
        return checkNotNull(serviceNode.getAccountAgeWitnessService()).getWitnessByHashAsHex(hash)
                .map(AccountAgeWitness::getDate)
                .orElse(-1L);
    }
}
