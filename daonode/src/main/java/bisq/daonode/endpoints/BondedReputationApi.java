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

import bisq.core.dao.governance.bond.reputation.BondedReputationRepository;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;

import bisq.common.util.Hex;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;



import bisq.daonode.DaoNodeRestApiApplication;
import bisq.daonode.ServiceNode;
import bisq.daonode.dto.BondedReputationDto;
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
 * Endpoint for getting the bonded reputation data from a given block height.
 * Used for reputation system in Bisq 2.
 * <a href="http://localhost:8082/api/v1/bonded-reputation/get-bonded-reputation/0">Request with block height 0</a>
 */
@Slf4j
@Path("/bonded-reputation")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Bonded reputation API")
public class BondedReputationApi {
    private static final String DESC_BLOCK_HEIGHT = "The block height from which we request the bonded reputation data";
    private final BondedReputationRepository bondedReputationRepository;
    private final DaoStateService daoStateService;

    public BondedReputationApi(@Context Application application) {
        ServiceNode serviceNode = ((DaoNodeRestApiApplication) application).getServiceNode();
        daoStateService = serviceNode.getDaoStateService();
        bondedReputationRepository = serviceNode.getBondedReputationRepository();
    }

    @Operation(description = "Request the bonded reputation data")
    @ApiResponse(responseCode = "200", description = "The bonded reputation data",
            content = {@Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(allOf = BondedReputationDto.class))}
    )
    @GET
    @Path("get-bonded-reputation/{block-height}")
    public List<BondedReputationDto> getBondedReputation(@Parameter(description = DESC_BLOCK_HEIGHT)
                                                         @PathParam("block-height")
                                                         int fromBlockHeight) {
        return bondedReputationRepository.getActiveBonds().stream()
                .map(bondedReputation -> {
                    Optional<Tx> optionalTx = daoStateService.getTx(bondedReputation.getLockupTxId());
                    if (optionalTx.isEmpty()) {
                        return null;
                    }
                    Tx tx = optionalTx.get();
                    int blockHeight = tx.getBlockHeight();
                    if (blockHeight >= fromBlockHeight) {
                        return new BondedReputationDto(bondedReputation.getAmount(),
                                tx.getTime(),
                                Hex.encode(bondedReputation.getBondedAsset().getHash()),
                                blockHeight,
                                bondedReputation.getLockTime()
                        );
                    } else {
                        return null;
                    }
                }).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
