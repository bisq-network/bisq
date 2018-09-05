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

package bisq.core.btc.blockchain.providers;

import bisq.network.http.HttpClient;

import bisq.common.app.Log;

import org.bitcoinj.core.Coin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.inject.Inject;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockrIOProvider extends BlockchainTxProvider {
    private static final Logger log = LoggerFactory.getLogger(BlockrIOProvider.class);

    @Inject
    public BlockrIOProvider(HttpClient httpClient) {
        super(httpClient, "https://btc.blockr.io/api/v1/tx/info/");
    }

    @Override
    public Coin getFee(String transactionId) throws IOException {
        Log.traceCall("transactionId=" + transactionId);
        try {
            JsonObject data = new JsonParser()
                    .parse(httpClient.requestWithGET(transactionId, "User-Agent", ""))
                    .getAsJsonObject()
                    .get("data")
                    .getAsJsonObject();
            return Coin.parseCoin(data
                    .get("fee")
                    .getAsString());
        } catch (IOException e) {
            log.debug("Error at requesting transaction data from block explorer " + httpClient + "\n" +
                    "Error =" + e.getMessage());
            throw e;
        }
    }

    @Override
    public String toString() {
        return "BlockrIOProvider{" +
                '}';
    }
}
