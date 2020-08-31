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
class XmrTxProofParser {
    static XmrTxProofResult parse(XmrTxProofModel xmrTxProofModel, String jsonTxt) {
        String txHash = xmrTxProofModel.getTxHash();
        try {
            JsonObject json = new Gson().fromJson(jsonTxt, JsonObject.class);
            if (json == null) {
                return new XmrTxProofResult(XmrTxProofResult.State.API_INVALID, "Empty json");
            }
            // there should always be "data" and "status" at the top level
            if (json.get("data") == null || !json.get("data").isJsonObject() || json.get("status") == null) {
                return new XmrTxProofResult(XmrTxProofResult.State.API_INVALID, "Missing data / status fields");
            }
            JsonObject jsonData = json.get("data").getAsJsonObject();
            String jsonStatus = json.get("status").getAsString();
            if (jsonStatus.matches("fail")) {
                // the API returns "fail" until the transaction has successfully reached the mempool.
                // we return TX_NOT_FOUND which will cause a retry later
                return new XmrTxProofResult(XmrTxProofResult.State.TX_NOT_FOUND);
            } else if (!jsonStatus.matches("success")) {
                return new XmrTxProofResult(XmrTxProofResult.State.API_FAILURE, "Unhandled status value");
            }

            // validate that the address matches
            JsonElement jsonAddress = jsonData.get("address");
            if (jsonAddress == null) {
                return new XmrTxProofResult(XmrTxProofResult.State.API_INVALID, "Missing address field");
            } else {
                String expectedAddressHex = CryptoNoteUtils.convertToRawHex(xmrTxProofModel.getRecipientAddress());
                if (!jsonAddress.getAsString().equalsIgnoreCase(expectedAddressHex)) {
                    log.warn("address {}, expected: {}", jsonAddress.getAsString(), expectedAddressHex);
                    return new XmrTxProofResult(XmrTxProofResult.State.ADDRESS_INVALID);
                }
            }

            // validate that the txHash matches
            JsonElement jsonTxHash = jsonData.get("tx_hash");
            if (jsonTxHash == null) {
                return new XmrTxProofResult(XmrTxProofResult.State.API_INVALID, "Missing tx_hash field");
            } else {
                if (!jsonTxHash.getAsString().equalsIgnoreCase(txHash)) {
                    log.warn("txHash {}, expected: {}", jsonTxHash.getAsString(), txHash);
                    return new XmrTxProofResult(XmrTxProofResult.State.TX_HASH_INVALID);
                }
            }

            // validate that the txKey matches
            JsonElement jsonViewkey = jsonData.get("viewkey");
            if (jsonViewkey == null) {
                return new XmrTxProofResult(XmrTxProofResult.State.API_INVALID, "Missing viewkey field");
            } else {
                if (!jsonViewkey.getAsString().equalsIgnoreCase(xmrTxProofModel.getTxKey())) {
                    log.warn("viewkey {}, expected: {}", jsonViewkey.getAsString(), xmrTxProofModel.getTxKey());
                    return new XmrTxProofResult(XmrTxProofResult.State.TX_KEY_INVALID);
                }
            }

            // validate that the txDate matches within tolerance
            // (except that in dev mode we let this check pass anyway)
            JsonElement jsonTimestamp = jsonData.get("tx_timestamp");
            if (jsonTimestamp == null) {
                return new XmrTxProofResult(XmrTxProofResult.State.API_INVALID, "Missing tx_timestamp field");
            } else {
                long tradeDateSeconds = xmrTxProofModel.getTradeDate().getTime() / 1000;
                long difference = tradeDateSeconds - jsonTimestamp.getAsLong();
                // Accept up to 2 hours difference. Some tolerance is needed if users clock is out of sync
                if (difference > TimeUnit.HOURS.toSeconds(2) && !DevEnv.isDevMode()) {
                    log.warn("tx_timestamp {}, tradeDate: {}, difference {}",
                            jsonTimestamp.getAsLong(), tradeDateSeconds, difference);
                    return new XmrTxProofResult(XmrTxProofResult.State.TRADE_DATE_NOT_MATCHING);
                }
            }

            // calculate how many confirms are still needed
            int confirmations;
            JsonElement jsonConfirmations = jsonData.get("tx_confirmations");
            if (jsonConfirmations == null) {
                return new XmrTxProofResult(XmrTxProofResult.State.API_INVALID, "Missing tx_confirmations field");
            } else {
                confirmations = jsonConfirmations.getAsInt();
                log.info("Confirmations: {}, xmr txHash: {}", confirmations, txHash);
            }

            // iterate through the list of outputs, one of them has to match the amount we are trying to verify.
            // check that the "match" field is true as well as validating the amount value
            // (except that in dev mode we allow any amount as valid)
            JsonArray jsonOutputs = jsonData.get("outputs").getAsJsonArray();
            boolean anyMatchFound = false;
            boolean amountMatches = false;
            for (int i = 0; i < jsonOutputs.size(); i++) {
                JsonObject out = jsonOutputs.get(i).getAsJsonObject();
                if (out.get("match").getAsBoolean()) {
                    anyMatchFound = true;
                    long jsonAmount = out.get("amount").getAsLong();
                    amountMatches = jsonAmount == xmrTxProofModel.getAmount();
                    if (amountMatches) {
                        break;
                    }
                }
            }

            // None of the outputs had a match entry
            if (!anyMatchFound) {
                return new XmrTxProofResult(XmrTxProofResult.State.NO_MATCH_FOUND);
            }

            // None of the outputs had a match entry
            if (!amountMatches) {
                return new XmrTxProofResult(XmrTxProofResult.State.AMOUNT_NOT_MATCHING);
            }

            int confirmsRequired = xmrTxProofModel.getConfirmsRequired();
            if (confirmations < confirmsRequired) {
                XmrTxProofResult xmrTxProofResult = new XmrTxProofResult(XmrTxProofResult.State.PENDING_CONFIRMATIONS);
                xmrTxProofResult.setNumConfirmations(confirmations);
                xmrTxProofResult.setRequiredConfirmations(confirmsRequired);
                return xmrTxProofResult;
            } else {
                return new XmrTxProofResult(XmrTxProofResult.State.SINGLE_SERVICE_SUCCEEDED);
            }

        } catch (JsonParseException | NullPointerException e) {
            return new XmrTxProofResult(XmrTxProofResult.State.API_INVALID, e.toString());
        }
    }
}
