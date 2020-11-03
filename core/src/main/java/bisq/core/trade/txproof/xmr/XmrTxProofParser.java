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

package bisq.core.trade.txproof.xmr;

import bisq.core.trade.txproof.AssetTxProofParser;

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
public class XmrTxProofParser implements AssetTxProofParser<XmrTxProofRequest.Result, XmrTxProofModel> {
    public static final long MAX_DATE_TOLERANCE = TimeUnit.HOURS.toSeconds(2);

    XmrTxProofParser() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("SpellCheckingInspection")
    @Override
    public XmrTxProofRequest.Result parse(XmrTxProofModel model, String jsonTxt) {
        String txHash = model.getTxHash();
        try {
            JsonObject json = new Gson().fromJson(jsonTxt, JsonObject.class);
            if (json == null) {
                return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error("Empty json"));
            }
            // there should always be "data" and "status" at the top level
            if (json.get("data") == null || !json.get("data").isJsonObject() || json.get("status") == null) {
                return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error("Missing data / status fields"));
            }
            JsonObject jsonData = json.get("data").getAsJsonObject();
            String jsonStatus = json.get("status").getAsString();
            if (jsonStatus.matches("fail")) {
                // The API returns "fail" until the transaction has successfully reached the mempool or if request
                // contained invalid data.
                // We return TX_NOT_FOUND which will cause a retry later
                return XmrTxProofRequest.Result.PENDING.with(XmrTxProofRequest.Detail.TX_NOT_FOUND);
            } else if (!jsonStatus.matches("success")) {
                return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error("Unhandled status value"));
            }

            // validate that the address matches
            JsonElement jsonAddress = jsonData.get("address");
            if (jsonAddress == null) {
                return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error("Missing address field"));
            } else {
                String expectedAddressHex = CryptoNoteUtils.getRawSpendKeyAndViewKey(model.getRecipientAddress());
                if (!jsonAddress.getAsString().equalsIgnoreCase(expectedAddressHex)) {
                    log.warn("Address from json result (convertToRawHex):\n{}\nExpected (convertToRawHex):\n{}\nRecipient address:\n{}",
                            jsonAddress.getAsString(), expectedAddressHex, model.getRecipientAddress());
                    return XmrTxProofRequest.Result.FAILED.with(XmrTxProofRequest.Detail.ADDRESS_INVALID);
                }
            }

            // validate that the txHash matches
            JsonElement jsonTxHash = jsonData.get("tx_hash");
            if (jsonTxHash == null) {
                return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error("Missing tx_hash field"));
            } else {
                if (!jsonTxHash.getAsString().equalsIgnoreCase(txHash)) {
                    log.warn("txHash {}, expected: {}", jsonTxHash.getAsString(), txHash);
                    return XmrTxProofRequest.Result.FAILED.with(XmrTxProofRequest.Detail.TX_HASH_INVALID);
                }
            }

            // validate that the txKey matches
            JsonElement jsonViewkey = jsonData.get("viewkey");
            if (jsonViewkey == null) {
                return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error("Missing viewkey field"));
            } else {
                if (!jsonViewkey.getAsString().equalsIgnoreCase(model.getTxKey())) {
                    log.warn("viewkey {}, expected: {}", jsonViewkey.getAsString(), model.getTxKey());
                    return XmrTxProofRequest.Result.FAILED.with(XmrTxProofRequest.Detail.TX_KEY_INVALID);
                }
            }

            // validate that the txDate matches within tolerance
            // (except that in dev mode we let this check pass anyway)
            JsonElement jsonTimestamp = jsonData.get("tx_timestamp");
            if (jsonTimestamp == null) {
                return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error("Missing tx_timestamp field"));
            } else {
                long tradeDateSeconds = model.getTradeDate().getTime() / 1000;
                long difference = tradeDateSeconds - jsonTimestamp.getAsLong();
                // Accept up to 2 hours difference. Some tolerance is needed if users clock is out of sync
                if (difference > MAX_DATE_TOLERANCE && !DevEnv.isDevMode()) {
                    log.warn("tx_timestamp {}, tradeDate: {}, difference {}",
                            jsonTimestamp.getAsLong(), tradeDateSeconds, difference);
                    return XmrTxProofRequest.Result.FAILED.with(XmrTxProofRequest.Detail.TRADE_DATE_NOT_MATCHING);
                }
            }

            // calculate how many confirms are still needed
            int confirmations;
            JsonElement jsonConfirmations = jsonData.get("tx_confirmations");
            if (jsonConfirmations == null) {
                return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error("Missing tx_confirmations field"));
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
                    amountMatches = jsonAmount == model.getAmount();
                    if (amountMatches) {
                        break;
                    } else {
                        log.warn("amount {}, expected: {}", jsonAmount, model.getAmount());
                    }
                }
            }

            // None of the outputs had a match entry
            if (!anyMatchFound) {
                return XmrTxProofRequest.Result.FAILED.with(XmrTxProofRequest.Detail.NO_MATCH_FOUND);
            }

            // None of the outputs had a match entry
            if (!amountMatches) {
                return XmrTxProofRequest.Result.FAILED.with(XmrTxProofRequest.Detail.AMOUNT_NOT_MATCHING);
            }

            int confirmsRequired = model.getNumRequiredConfirmations();
            if (confirmations < confirmsRequired) {
                return XmrTxProofRequest.Result.PENDING.with(XmrTxProofRequest.Detail.PENDING_CONFIRMATIONS.numConfirmations(confirmations));
            } else {
                return XmrTxProofRequest.Result.SUCCESS.with(XmrTxProofRequest.Detail.SUCCESS.numConfirmations(confirmations));
            }

        } catch (JsonParseException | NullPointerException e) {
            return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error(e.toString()));
        } catch (CryptoNoteUtils.CryptoNoteException e) {
            return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.ADDRESS_INVALID.error(e.toString()));
        }
    }
}
