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

package bisq.daonode.web.jdk.handler;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;

import bisq.common.util.Hex;

import java.io.IOException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.daonode.web.jdk.handler.HandlerUtil.sendResponse;
import static bisq.daonode.web.jdk.handler.HandlerUtil.toJson;
import static bisq.daonode.web.jdk.handler.HandlerUtil.wrapErrorResponse;
import static bisq.daonode.web.jdk.handler.HandlerUtil.wrapResponse;
import static bisq.daonode.web.jdk.handler.ResourcePathElement.BLOCKHEIGHT;



import bisq.daonode.dto.ProofOfBurnDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;


/**
 * Handles daonode/proofofburn requests.  Request URLs must match:
 * http://localhost:<port>/daonode/proofofburn/blockheight/<blockheight-value>
 *
 * Example:  http://localhost:8080/daonode/proofofburn/blockheight/731270
 */
@Slf4j
class GetProofOfBurnHandler implements HttpHandler {

    private final DaoStateService daoStateService;
    private final RequestSpec requestSpec;

    /**
     * A new handler instance must be used for each request.  We do not want to parse
     * details from each request URI more than once;  they are passed to this constructor
     * from the RestHandler via the RequestSpec argument.
     *
     * @param daoStateService DaoStateService singleton
     * @param requestSpec RESTful request details, including parsed URL parameters
     */
    public GetProofOfBurnHandler(DaoStateService daoStateService, RequestSpec requestSpec) {
        this.daoStateService = daoStateService;
        this.requestSpec = requestSpec;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        try {
            if (daoStateService == null) {
                log.warn("DAO Node daoStateService is null;  OK during web server dev/test.");
                sendResponse(httpExchange, wrapResponse("[]"));
            } else {
                int blockHeight = requestSpec.getIntParam(BLOCKHEIGHT);
                log.info("Requesting POB for blockheight {}.", blockHeight);
                List<ProofOfBurnDto> data = getProofOfBurnDtoList(blockHeight);
                if (data != null) {
                    sendResponse(httpExchange, wrapResponse(toJson(data)));
                } else {
                    log.error("DAO Node Proof of Burn data for blockHeight {} is null.", blockHeight);
                    sendResponse(500,
                            httpExchange,
                            wrapErrorResponse(toJson("DAO Node proof of burn data is null.")));
                }
            }
        } catch (RuntimeException ex) {
            sendResponse(500,
                    httpExchange,
                    wrapErrorResponse(toJson(ex.getMessage())));
        }
    }

    private List<ProofOfBurnDto> getProofOfBurnDtoList(int fromBlockHeight) {
        return daoStateService.getProofOfBurnTxs().stream()
                .filter(tx -> tx.getBlockHeight() >= fromBlockHeight)
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
