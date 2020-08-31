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

package bisq.core.trade.autoconf.xmr;

import bisq.asset.CryptoNoteUtils;

import bisq.common.app.DevEnv;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XmrProofParser {
    static public XmrAutoConfirmResult parse(XmrProofInfo xmrProofInfo, String jsonTxt) {
        String txHash = xmrProofInfo.getTxHash();
        try {
            JsonObject json = new Gson().fromJson(jsonTxt, JsonObject.class);
            if (json == null) {
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.API_INVALID, "Empty json");
            }
            // there should always be "data" and "status" at the top level
            if (json.get("data") == null || !json.get("data").isJsonObject() || json.get("status") == null) {
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.API_INVALID, "Missing data / status fields");
            }
            JsonObject jsonData = json.get("data").getAsJsonObject();
            String jsonStatus = json.get("status").getAsString();
            if (jsonStatus.matches("fail")) {
                // the API returns "fail" until the transaction has successfully reached the mempool.
                // we return TX_NOT_FOUND which will cause a retry later
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.TX_NOT_FOUND, null);
            } else if (!jsonStatus.matches("success")) {
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.API_FAILURE, "Unhandled status value");
            }

            // validate that the address matches
            JsonElement jsonAddress = jsonData.get("address");
            if (jsonAddress == null) {
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.API_INVALID, "Missing address field");
            } else {
                String expectedAddressHex = CryptoNoteUtils.convertToRawHex(xmrProofInfo.getRecipientAddress());
                if (!jsonAddress.getAsString().equalsIgnoreCase(expectedAddressHex)) {
                    log.warn("address {}, expected: {}", jsonAddress.getAsString(), expectedAddressHex);
                    return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.ADDRESS_INVALID, null);
                }
            }

            // validate that the txHash matches
            JsonElement jsonTxHash = jsonData.get("tx_hash");
            if (jsonTxHash == null) {
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.API_INVALID, "Missing tx_hash field");
            } else {
                if (!jsonTxHash.getAsString().equalsIgnoreCase(txHash)) {
                    log.warn("txHash {}, expected: {}", jsonTxHash.getAsString(), txHash);
                    return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.TX_HASH_INVALID, null);
                }
            }

            // validate that the txKey matches
            JsonElement jsonViewkey = jsonData.get("viewkey");
            if (jsonViewkey == null) {
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.API_INVALID, "Missing viewkey field");
            } else {
                if (!jsonViewkey.getAsString().equalsIgnoreCase(xmrProofInfo.getTxKey())) {
                    log.warn("viewkey {}, expected: {}", jsonViewkey.getAsString(), xmrProofInfo.getTxKey());
                    return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.TX_KEY_INVALID, null);
                }
            }

            // validate that the txDate matches within tolerance
            // (except that in dev mode we let this check pass anyway)
            JsonElement jsonTimestamp = jsonData.get("tx_timestamp");
            if (jsonTimestamp == null) {
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.API_INVALID, "Missing tx_timestamp field");
            } else {
                long tradeDateSeconds = xmrProofInfo.getTradeDate().getTime() / 1000;
                long difference = tradeDateSeconds - jsonTimestamp.getAsLong();
                // Accept up to 2 hours difference. Some tolerance is needed if users clock is out of sync
                if (difference > TimeUnit.HOURS.toSeconds(2) && !DevEnv.isDevMode()) {
                    log.warn("tx_timestamp {}, tradeDate: {}, difference {}",
                            jsonTimestamp.getAsLong(), tradeDateSeconds, difference);
                    return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.TRADE_DATE_NOT_MATCHING, null);
                }
            }

            // calculate how many confirms are still needed
            int confirmations;
            JsonElement jsonConfirmations = jsonData.get("tx_confirmations");
            if (jsonConfirmations == null) {
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.API_INVALID, "Missing tx_confirmations field");
            } else {
                confirmations = jsonConfirmations.getAsInt();
                log.info("Confirmations: {}, xmr txHash: {}", confirmations, txHash);
            }

            // iterate through the list of outputs, one of them has to match the amount we are trying to verify.
            // check that the "match" field is true as well as validating the amount value
            // (except that in dev mode we allow any amount as valid)
            JsonArray jsonOutputs = jsonData.get("outputs").getAsJsonArray();
            boolean anyMatchFound = false;
            for (int i = 0; i < jsonOutputs.size(); i++) {
                JsonObject out = jsonOutputs.get(i).getAsJsonObject();
                if (out.get("match").getAsBoolean()) {
                    anyMatchFound = true;
                    long jsonAmount = out.get("amount").getAsLong();
                    if (jsonAmount == xmrProofInfo.getAmount() || DevEnv.isDevMode()) {   // any amount ok in dev mode
                        int confirmsRequired = xmrProofInfo.getConfirmsRequired();
                        if (confirmations < confirmsRequired)
                            // we return TX_NOT_CONFIRMED which will cause a retry later
                            return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.TX_NOT_CONFIRMED, confirmations, confirmsRequired);
                        else
                            return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.PROOF_OK, confirmations, confirmsRequired);
                    }
                }
            }

            // None of the outputs had a match entry
            if (!anyMatchFound && !DevEnv.isDevMode()) {
                return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.NO_MATCH_FOUND, null);
            }

            // reaching this point means there was no matching amount
            return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.AMOUNT_NOT_MATCHING, null);

        } catch (JsonParseException | NullPointerException e) {
            return new XmrAutoConfirmResult(XmrAutoConfirmResult.State.API_INVALID, e.toString());
        }
    }
}
