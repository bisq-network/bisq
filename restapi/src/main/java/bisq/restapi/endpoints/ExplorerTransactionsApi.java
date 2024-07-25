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

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxType;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;



import bisq.restapi.BlockDataToJsonConverter;
import bisq.restapi.RestApi;
import bisq.restapi.RestApiMain;
import bisq.restapi.dto.JsonTx;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Slf4j
@Path("/explorer/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "TRANSACTIONS API")
public class ExplorerTransactionsApi {
    private final DaoStateService daoStateService;
    private final RestApi restApi;

    public ExplorerTransactionsApi(@Context Application application) {
        restApi = ((RestApiMain) application).getRestApi();
        daoStateService = restApi.getDaoStateService();
    }

    @GET
    @Path("get-bsq-tx/{txid}")
    public JsonTx getTx(@Parameter(description = "TxId")
                        @PathParam("txid") String txId) {
        restApi.checkDaoReady();
        Optional<JsonTx> jsonTx = daoStateService.getUnorderedTxStream()
                .filter(t -> t.getId().equals(txId))
                .map(tx -> BlockDataToJsonConverter.getJsonTx(daoStateService, tx))
                .findFirst();
        if (jsonTx.isPresent()) {
            log.info("supplying tx {} to client.", txId);
            return jsonTx.get();
        }
        log.warn("txid {} not found!", txId);
        return null;
    }

    @GET
    @Path("get-bsq-tx-for-addr/{addr}")
    public List<JsonTx> getBisqTxForAddr(@PathParam("addr") String address) {
        restApi.checkDaoReady();
        // In case we get a prefixed address marking BSQ addresses we remove the prefix
        if (address.startsWith("B")) {
            address = address.substring(1, address.length());
        }
        String finalAddress = address;
        List<JsonTx> result = daoStateService.getTxIdSetByAddress().entrySet().stream()
                .filter(e -> e.getKey().equals(finalAddress))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .flatMap(txId -> daoStateService.getTx(txId).stream())
                .map(tx -> BlockDataToJsonConverter.getJsonTx(daoStateService, tx))
                .collect(Collectors.toList());
        log.info("getBisqTxForAddr: returning {} items.", result.size());
        return result;
    }

    @GET
    @Path("query-txs-paginated/{start}/{count}/{filters}")
    public List<JsonTx> queryTxsPaginated(@PathParam("start") int start,
                                          @PathParam("count") int count,
                                          @PathParam("filters") String filters) {
        restApi.checkDaoReady();
        log.info("filters: {}", filters);
        List<JsonTx> jsonTxs = daoStateService.getUnorderedTxStream()
                .sorted(Comparator.comparing(BaseTx::getTime).reversed())
                .filter(tx -> hasMatchingTxType(tx, filters))
                .skip(start)
                .limit(count)
                .map(tx -> BlockDataToJsonConverter.getJsonTx(daoStateService, tx))
                .collect(Collectors.toList());
        log.info("supplying {} jsonTxs to client from index {}", jsonTxs.size(), start);
        return jsonTxs;
    }

    private boolean hasMatchingTxType(Tx tx, String filters) {
        String[] filterTokens = filters.split("~");
        if (filterTokens.length < 1 || filters.equalsIgnoreCase("~")) {
            return true;
        }
        for (String filter : filterTokens) {
            try {
                TxType txType = Enum.valueOf(TxType.class, filter);
                if (tx.getTxType() == txType) {
                    return true;
                }
            } catch (Exception e) {
                log.error("Could not resolve TxType Enum from " + filter, e);
                return false;
            }
        }
        return false;
    }

}
