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

package bisq.daonode.controller;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;

import bisq.common.util.Hex;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;



import bisq.daonode.dto.ProofOfBurnDto;

@Slf4j
public class ProofOfBurnController implements RestController {
    private final DaoStateService daoStateService;

    public ProofOfBurnController(DaoStateService daoStateService) {
        this.daoStateService = daoStateService;
    }

    private String getProofOfBurnDtoList(int fromBlockHeight) {
        List<ProofOfBurnDto> proofOfBurnDtoList = daoStateService.getProofOfBurnTxs().stream()
                .filter(tx -> tx.getBlockHeight() >= fromBlockHeight)
                .map(tx -> new ProofOfBurnDto(tx.getBurntBsq(),
                        tx.getBlockHeight(),
                        tx.getTime(),
                        getHash(tx)))
                .collect(Collectors.toList());
        return toJson(proofOfBurnDtoList);
    }

    // We strip out the version bytes
    private String getHash(Tx tx) {
        byte[] opReturnData = tx.getLastTxOutput().getOpReturnData();
        if (opReturnData == null) {
            return "";
        }
        return Hex.encode(Arrays.copyOfRange(opReturnData, 2, 22));
    }

    @Override
    public String getResponse(Optional<String> fromBlockHeightQuery) {
        int fromBlockHeight = fromBlockHeightQuery.map(height -> {
            try {
                // todo depending on the query format we support, will require more
                // sophisticated parsing
                return Integer.parseInt(height);
            } catch (Throwable t) {
                log.error("Invalid fromBlockHeightQuery. Should be of format `?1234`", t);
                return 0;
            }
        }).orElse(0);
        return getProofOfBurnDtoList(fromBlockHeight);
    }
}
