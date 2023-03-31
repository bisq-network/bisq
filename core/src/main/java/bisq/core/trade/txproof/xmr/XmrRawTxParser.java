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

import bisq.common.app.DevEnv;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XmrRawTxParser implements AssetTxProofParser<XmrTxProofRequest.Result, XmrTxProofModel> {
    XmrRawTxParser() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public XmrTxProofRequest.Result parse(XmrTxProofModel model, String jsonTxt) {
        return parse(jsonTxt);
    }

    @SuppressWarnings("SpellCheckingInspection")
    @Override
    public XmrTxProofRequest.Result parse(String jsonTxt) {
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

            JsonElement jsonUnlockTime = jsonData.get("unlock_time");
            if (jsonUnlockTime == null) {
                return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error("Missing unlock_time field"));
            } else {
                long unlockTime = jsonUnlockTime.getAsLong();
                if (unlockTime != 0 && !DevEnv.isDevMode()) {
                    log.warn("Invalid unlock_time {}", unlockTime);
                    return XmrTxProofRequest.Result.FAILED.with(XmrTxProofRequest.Detail.INVALID_UNLOCK_TIME.error("Invalid unlock_time"));
                }
            }
            return XmrTxProofRequest.Result.SUCCESS.with(XmrTxProofRequest.Detail.SUCCESS);
        } catch (JsonParseException | NullPointerException e) {
            return XmrTxProofRequest.Result.ERROR.with(XmrTxProofRequest.Detail.API_INVALID.error(e.toString()));
        }
    }
}
