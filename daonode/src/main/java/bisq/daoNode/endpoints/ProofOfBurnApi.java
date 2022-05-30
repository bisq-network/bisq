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

package bisq.daoNode.endpoints;

import bisq.core.dao.state.model.blockchain.Tx;

import bisq.common.util.Hex;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;



import bisq.daoNode.DaoNode;
import bisq.daoNode.DaoNodeRestApiApplication;
import bisq.daoNode.dto.ProofOfBurnDto;
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

@Slf4j
@Path("/proof-of-burn")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Proof of burn API")
public class ProofOfBurnApi {
    public static final String DESC_BLOCK_HEIGHT = "The block height from which we request the proof of burn data";
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
                                               int blockHeight) {
        return checkNotNull(daoNode.getDaoStateService()).getProofOfBurnTxs().stream()
                .filter(tx -> tx.getBlockHeight() >= blockHeight)
                .map(tx -> new ProofOfBurnDto(tx.getId(),
                        tx.getBurntBsq(),
                        tx.getBlockHeight(),
                        tx.getTime(),
                        getHash(tx)))
                .collect(Collectors.toList());
    }

    // We strip out the version bytes
    private String getHash(Tx tx) {
        byte[] opReturnData = tx.getLastTxOutput().getOpReturnData();
        if (opReturnData == null) {
            return "";
        }
        return Hex.encode(Arrays.copyOfRange(opReturnData, 2, 22));
    }
}
