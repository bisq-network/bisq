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

import bisq.core.dao.governance.proofofburn.ProofOfBurnService;

import bisq.common.util.Hex;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;



import bisq.daonode.DaoNode;
import bisq.daonode.DaoNodeRestApiApplication;
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
 * Endpoint for getting the proof of burn data from a given block height.
 * Used for reputation system in Bisq 2.
 * <a href="http://localhost:8082/api/v1/proof-of-burn/get-proof-of-burn/0">Request with block height 0</a>
 */
@Slf4j
@Path("/proof-of-burn")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Proof of burn API")
public class ProofOfBurnApi {
    private static final String DESC_BLOCK_HEIGHT = "The block height from which we request the proof of burn data";
    private final DaoNode daoNode;

    public ProofOfBurnApi(@Context Application application) {
        daoNode = ((DaoNodeRestApiApplication) application).getDaoNode();
    }

    @Operation(description = "Request the proof of burn data")
    @ApiResponse(responseCode = "200", description = "The proof of burn data",
            content = {@Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(allOf = ProofOfBurnDto.class))}
    )
    @GET
    @Path("get-proof-of-burn/{block-height}")
    public List<ProofOfBurnDto> getProofOfBurn(@Parameter(description = DESC_BLOCK_HEIGHT)
                                               @PathParam("block-height")
                                               int fromBlockHeight) {
        return checkNotNull(daoNode.getDaoStateService()).getProofOfBurnTxs().stream()
                .filter(tx -> tx.getBlockHeight() >= fromBlockHeight)
                .map(tx -> new ProofOfBurnDto(tx.getBurntBsq(),
                        tx.getTime(),
                        Hex.encode(ProofOfBurnService.getHashFromOpReturnData(tx)),
                        tx.getBlockHeight()))
                .collect(Collectors.toList());
    }
}
