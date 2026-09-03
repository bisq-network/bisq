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

package bisq.core.provider.mempool;

import org.bitcoinj.core.Sha256Hash;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public record MempoolTxStatus(String txId, boolean confirmed, long blockHeight) {
    public MempoolTxStatus {
        txId = Sha256Hash.wrap(checkNotNull(txId, "txId must not be null")).toString();
        checkArgument(confirmed == (blockHeight > 0),
                "A confirmed transaction must have a positive block height and an unconfirmed transaction must use 0");
    }

    public static MempoolTxStatus fromJson(String requestedTxId, String json) {
        Sha256Hash requestedHash = Sha256Hash.wrap(checkNotNull(requestedTxId,
                "requestedTxId must not be null"));
        JsonObject root = new Gson().fromJson(checkNotNull(json, "Transaction status JSON must not be null"),
                JsonObject.class);
        checkNotNull(root, "Transaction status JSON must contain an object");

        JsonElement txIdElement = checkNotNull(root.get("txid"),
                "Transaction status JSON must contain txid");
        Sha256Hash returnedHash = Sha256Hash.wrap(txIdElement.getAsString());
        checkArgument(requestedHash.equals(returnedHash),
                "Transaction status ID %s does not match requested ID %s",
                returnedHash,
                requestedHash);

        JsonObject status = checkNotNull(root.getAsJsonObject("status"),
                "Transaction status JSON must contain a status object");
        JsonElement confirmedElement = checkNotNull(status.get("confirmed"),
                "Transaction status JSON must contain status.confirmed");
        boolean confirmed = confirmedElement.getAsBoolean();
        if (!confirmed) {
            return new MempoolTxStatus(requestedHash.toString(), false, 0);
        }

        JsonElement blockHeightElement = checkNotNull(status.get("block_height"),
                "Confirmed transaction status must contain status.block_height");
        long blockHeight = blockHeightElement.getAsLong();
        checkArgument(blockHeight > 0, "Confirmed transaction block height must be positive");
        return new MempoolTxStatus(requestedHash.toString(), true, blockHeight);
    }
}
