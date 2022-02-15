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

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterManager;

import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

import static bisq.core.util.coin.CoinUtil.maxCoin;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Getter
public class TxValidator {
    private static final Date USE_FEE_FROM_FILTER_ACTIVATION_DATE = Utilities.getUTCDate(2022, GregorianCalendar.JANUARY, 1);
    private final static double FEE_TOLERANCE = 0.95;     // we expect fees to be at least 95% of target
    private final static long BLOCK_TOLERANCE = 599999;  // allow really old offers with weird fee addresses

    private final DaoStateService daoStateService;
    private final FilterManager filterManager;
    private long blockHeightAtOfferCreation; // Only set for maker.
    private final List<String> errorList;
    private final String txId;
    private Coin amount;
    @Nullable
    private Boolean isFeeCurrencyBtc = null;
    @Nullable
    private Long chainHeight;
    @Setter
    private String jsonTxt;

    public TxValidator(DaoStateService daoStateService,
                       String txId,
                       Coin amount,
                       @Nullable Boolean isFeeCurrencyBtc,
                       FilterManager filterManager) {
        this.daoStateService = daoStateService;
        this.txId = txId;
        this.amount = amount;
        this.isFeeCurrencyBtc = isFeeCurrencyBtc;
        this.filterManager = filterManager;
        this.errorList = new ArrayList<>();
        this.jsonTxt = "";
    }

    public TxValidator(DaoStateService daoStateService,
                       String txId,
                       Coin amount,
                       @Nullable Boolean isFeeCurrencyBtc,
                       long blockHeightAtOfferCreation,
                       FilterManager filterManager) {
        this.daoStateService = daoStateService;
        this.txId = txId;
        this.amount = amount;
        this.isFeeCurrencyBtc = isFeeCurrencyBtc;
        this.blockHeightAtOfferCreation = blockHeightAtOfferCreation;
        this.filterManager = filterManager;
        this.errorList = new ArrayList<>();
        this.jsonTxt = "";
    }

    public TxValidator(DaoStateService daoStateService, String txId, FilterManager filterManager) {
        this.daoStateService = daoStateService;
        this.txId = txId;
        this.chainHeight = (long) daoStateService.getChainHeight();
        this.filterManager = filterManager;
        this.errorList = new ArrayList<>();
        this.jsonTxt = "";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxValidator parseJsonValidateMakerFeeTx(String jsonTxt, List<String> btcFeeReceivers) {
        this.jsonTxt = jsonTxt;
        boolean status = initialSanityChecks(txId, jsonTxt);
        try {
            if (status) {
                if (checkNotNull(isFeeCurrencyBtc)) {
                    status = checkFeeAddressBTC(jsonTxt, btcFeeReceivers)
                            && checkFeeAmountBTC(jsonTxt, amount, true, getBlockHeightForFeeCalculation(jsonTxt));
                } else {
                    status = checkFeeAmountBSQ(jsonTxt, amount, true, getBlockHeightForFeeCalculation(jsonTxt));
                }
            }
        } catch (JsonSyntaxException e) {
            String s = "The maker fee tx JSON validation failed with reason: " + e.toString();
            log.info(s);
            errorList.add(s);
            status = false;
        }
        return endResult("Maker tx validation", status);
    }

    public TxValidator parseJsonValidateTakerFeeTx(String jsonTxt, List<String> btcFeeReceivers) {
        this.jsonTxt = jsonTxt;
        boolean status = initialSanityChecks(txId, jsonTxt);
        try {
            if (status) {
                if (isFeeCurrencyBtc == null) {
                    isFeeCurrencyBtc = checkFeeAddressBTC(jsonTxt, btcFeeReceivers);
                }
                if (isFeeCurrencyBtc) {
                    status = checkFeeAddressBTC(jsonTxt, btcFeeReceivers)
                            && checkFeeAmountBTC(jsonTxt, amount, false, getBlockHeightForFeeCalculation(jsonTxt));
                } else {
                    status = checkFeeAmountBSQ(jsonTxt, amount, false, getBlockHeightForFeeCalculation(jsonTxt));
                }
            }
        } catch (JsonSyntaxException e) {
            String s = "The taker fee tx JSON validation failed with reason: " + e.toString();
            log.info(s);
            errorList.add(s);
            status = false;
        }
        return endResult("Taker tx validation", status);
    }

    public long parseJsonValidateTx() {
        if (!initialSanityChecks(txId, jsonTxt)) {
            return -1;
        }
        return getTxConfirms(jsonTxt, chainHeight);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean checkFeeAddressBTC(String jsonTxt, List<String> btcFeeReceivers) {
        try {
            JsonArray jsonVout = getVinAndVout(jsonTxt).second;
            JsonObject jsonVout0 = jsonVout.get(0).getAsJsonObject();
            JsonElement jsonFeeAddress = jsonVout0.get("scriptpubkey_address");
            log.debug("fee address: {}", jsonFeeAddress.getAsString());
            if (btcFeeReceivers.contains(jsonFeeAddress.getAsString())) {
                return true;
            } else if (getBlockHeightForFeeCalculation(jsonTxt) < BLOCK_TOLERANCE) {
                log.info("Leniency rule, unrecognised fee receiver but its a really old offer so let it pass, {}", jsonFeeAddress.getAsString());
                return true;
            } else {
                String error = "fee address: " + jsonFeeAddress.getAsString() + " was not a known BTC fee receiver";
                errorList.add(error);
                log.info(error);
            }
        } catch (JsonSyntaxException e) {
            errorList.add(e.toString());
            log.warn(e.toString());
        }
        return false;
    }

    private boolean checkFeeAmountBTC(String jsonTxt, Coin tradeAmount, boolean isMaker, long blockHeight) {
        JsonArray jsonVin = getVinAndVout(jsonTxt).first;
        JsonArray jsonVout = getVinAndVout(jsonTxt).second;
        JsonObject jsonVin0 = jsonVin.get(0).getAsJsonObject();
        JsonObject jsonVout0 = jsonVout.get(0).getAsJsonObject();
        JsonElement jsonVIn0Value = jsonVin0.getAsJsonObject("prevout").get("value");
        JsonElement jsonFeeValue = jsonVout0.get("value");
        if (jsonVIn0Value == null || jsonFeeValue == null) {
            throw new JsonSyntaxException("vin/vout missing data");
        }
        long feeValue = jsonFeeValue.getAsLong();
        log.debug("BTC fee: {}", feeValue);

        Param minFeeParam = isMaker ? Param.MIN_MAKER_FEE_BTC : Param.MIN_TAKER_FEE_BTC;
        Coin expectedFee = calculateFee(tradeAmount,
                isMaker ? getMakerFeeRateBtc(blockHeight) : getTakerFeeRateBtc(blockHeight),
                minFeeParam);

        Coin feeValueAsCoin = Coin.valueOf(feeValue);
        long expectedFeeAsLong = expectedFee.getValue();
        String description = "Expected BTC fee: " + expectedFee + " sats , actual fee paid: " +
                feeValueAsCoin + " sats";
        if (expectedFeeAsLong == feeValue) {
            log.debug("The fee matched. " + description);
            return true;
        }

        if (expectedFeeAsLong < feeValue) {
            log.info("The fee was more than what we expected: " + description);
            return true;
        }

        double leniencyCalc = feeValue / (double) expectedFeeAsLong;
        if (leniencyCalc > FEE_TOLERANCE) {
            log.info("Leniency rule: the fee was low, but above {} of what was expected {} {}",
                    FEE_TOLERANCE, leniencyCalc, description);
            return true;
        }

        Optional<Boolean> result = maybeCheckAgainstFeeFromFilter(tradeAmount,
                isMaker,
                feeValueAsCoin,
                minFeeParam,
                true,
                description);
        if (result.isPresent()) {
            return result.get();
        }

        Param defaultFeeParam = isMaker ? Param.DEFAULT_MAKER_FEE_BTC : Param.DEFAULT_TAKER_FEE_BTC;
        if (feeExistsUsingDifferentDaoParam(tradeAmount, feeValueAsCoin, defaultFeeParam, minFeeParam)) {
            log.info("Leniency rule: the fee matches a different DAO parameter {}", description);
            return true;
        }

        String feeUnderpaidMessage = "UNDERPAID. " + description;
        errorList.add(feeUnderpaidMessage);
        log.info(feeUnderpaidMessage);
        return false;
    }

    // I think its better to postpone BSQ fee check once the BSQ trade fee tx is confirmed and then use the BSQ explorer to request the
    // BSQ fee to check if it is correct.
    // Otherwise the requirements here become very complicated and potentially impossible to verify as we don't know
    // if inputs and outputs are valid BSQ without the BSQ parser and confirmed transactions.
    private boolean checkFeeAmountBSQ(String jsonTxt, Coin tradeAmount, boolean isMaker, long blockHeight) {
        JsonArray jsonVin = getVinAndVout(jsonTxt).first;
        JsonArray jsonVout = getVinAndVout(jsonTxt).second;
        JsonObject jsonVin0 = jsonVin.get(0).getAsJsonObject();
        JsonObject jsonVout0 = jsonVout.get(0).getAsJsonObject();
        JsonElement jsonVIn0Value = jsonVin0.getAsJsonObject("prevout").get("value");
        JsonElement jsonFeeValue = jsonVout0.get("value");
        if (jsonVIn0Value == null || jsonFeeValue == null) {
            throw new JsonSyntaxException("vin/vout missing data");
        }
        Param minFeeParam = isMaker ? Param.MIN_MAKER_FEE_BSQ : Param.MIN_TAKER_FEE_BSQ;
        Coin expectedFee = calculateFee(tradeAmount,
                isMaker ? getMakerFeeRateBsq(blockHeight) : getTakerFeeRateBsq(blockHeight),
                minFeeParam);
        long feeValue = jsonVIn0Value.getAsLong() - jsonFeeValue.getAsLong();
        // if the first output (BSQ) is greater than the first input (BSQ) include the second input (presumably BSQ)
        if ((jsonFeeValue.getAsLong() > jsonVIn0Value.getAsLong() ||
                expectedFee.getValue() > jsonVIn0Value.getAsLong()) && jsonVin.size() > 2) {
            // in this case 2 or more UTXOs were spent to pay the fee:
            JsonObject jsonVin1 = jsonVin.get(1).getAsJsonObject();
            JsonElement jsonVIn1Value = jsonVin1.getAsJsonObject("prevout").get("value");
            feeValue += jsonVIn1Value.getAsLong();
        }
        log.debug("BURNT BSQ maker fee: {} BSQ ({} sats)", (double) feeValue / 100.0, feeValue);
        long expectedFeeAsLong = expectedFee.getValue();
        String description = String.format("Expected fee: %.2f BSQ, actual fee paid: %.2f BSQ",
                (double) expectedFeeAsLong / 100.0, (double) feeValue / 100.0);

        if (expectedFeeAsLong == feeValue) {
            log.debug("The fee matched. " + description);
            return true;
        }

        if (expectedFeeAsLong < feeValue) {
            log.info("The fee was more than what we expected. " + description);
            return true;
        }

        double leniencyCalc = feeValue / (double) expectedFeeAsLong;
        if (leniencyCalc > FEE_TOLERANCE) {
            log.info("Leniency rule: the fee was low, but above {} of what was expected {} {}", FEE_TOLERANCE, leniencyCalc, description);
            return true;
        }
        Coin feeValueAsCoin = Coin.valueOf(feeValue);

        Optional<Boolean> maybeTestFeeFromFilter = maybeCheckAgainstFeeFromFilter(tradeAmount,
                isMaker,
                feeValueAsCoin,
                minFeeParam,
                false,
                description);
        if (maybeTestFeeFromFilter.isPresent()) {
            return maybeTestFeeFromFilter.get();
        }

        Param defaultFeeParam = isMaker ? Param.DEFAULT_MAKER_FEE_BSQ : Param.DEFAULT_TAKER_FEE_BSQ;
        if (feeExistsUsingDifferentDaoParam(tradeAmount, Coin.valueOf(feeValue), defaultFeeParam, minFeeParam)) {
            log.info("Leniency rule: the fee matches a different DAO parameter {}", description);
            return true;
        }

        errorList.add(description);
        log.info(description);
        return false;
    }

    private static Tuple2<JsonArray, JsonArray> getVinAndVout(String jsonTxt) throws JsonSyntaxException {
        // there should always be "vout" at the top level
        // check that there are 2 or 3 vout elements: the fee, the reserved for trade, optional change
        JsonObject json = new Gson().fromJson(jsonTxt, JsonObject.class);
        if (json.get("vin") == null || json.get("vout") == null) {
            throw new JsonSyntaxException("missing vin/vout");
        }
        JsonArray jsonVin = json.get("vin").getAsJsonArray();
        JsonArray jsonVout = json.get("vout").getAsJsonArray();
        if (jsonVin == null || jsonVout == null || jsonVin.size() < 1 || jsonVout.size() < 2) {
            throw new JsonSyntaxException("not enough vins/vouts");
        }
        return new Tuple2<>(jsonVin, jsonVout);
    }

    private static boolean initialSanityChecks(String txId, String jsonTxt) {
        // there should always be "status" container element at the top level
        if (jsonTxt == null || jsonTxt.length() == 0) {
            return false;
        }
        JsonObject json = new Gson().fromJson(jsonTxt, JsonObject.class);
        if (json.get("status") == null) {
            return false;
        }
        // there should always be "txid" string element at the top level
        if (json.get("txid") == null) {
            return false;
        }
        // txid should match what we requested
        if (!txId.equals(json.get("txid").getAsString())) {
            return false;
        }
        JsonObject jsonStatus = json.get("status").getAsJsonObject();
        JsonElement jsonConfirmed = jsonStatus.get("confirmed");
        return (jsonConfirmed != null);
        // the json is valid and it contains a "confirmed" field then tx is known to mempool.space
        // we don't care if it is confirmed or not, just that it exists.
    }

    private static long getTxConfirms(String jsonTxt, long chainHeight) {
        long blockHeight = getTxBlockHeight(jsonTxt);
        if (blockHeight > 0) {
            return (chainHeight - blockHeight) + 1; // if it is in the current block it has 1 conf
        }
        return 0;  // 0 indicates unconfirmed
    }

    // we want the block height applicable for calculating the appropriate expected trading fees
    // if the tx is not yet confirmed, use current block tip, if tx is confirmed use the block it was confirmed at.
    private long getBlockHeightForFeeCalculation(String jsonTxt) {
        // For the maker we set the blockHeightAtOfferCreation from the offer
        if (blockHeightAtOfferCreation > 0) {
            return blockHeightAtOfferCreation;
        }

        long txBlockHeight = getTxBlockHeight(jsonTxt);
        if (txBlockHeight > 0) {
            return txBlockHeight;
        }

        return daoStateService.getChainHeight();
    }

    // this would be useful for the arbitrator verifying that the delayed payout tx is confirmed
    private static long getTxBlockHeight(String jsonTxt) {
        // there should always be "status" container element at the top level
        JsonObject json = new Gson().fromJson(jsonTxt, JsonObject.class);
        if (json.get("status") == null) {
            return -1L;
        }
        JsonObject jsonStatus = json.get("status").getAsJsonObject();
        JsonElement jsonConfirmed = jsonStatus.get("confirmed");
        if (jsonConfirmed == null) {
            return -1L;
        }
        if (jsonConfirmed.getAsBoolean()) {
            // it is confirmed, lets get the block height
            JsonElement jsonBlockHeight = jsonStatus.get("block_height");
            if (jsonBlockHeight == null) {
                return -1L; // block height error
            }
            return (jsonBlockHeight.getAsLong());
        }
        return 0L;  // in mempool, not confirmed yet
    }

    private Coin calculateFee(Coin amount, Coin feeRatePerBtc, Param minFeeParam) {
        double feePerBtcAsDouble = (double) feeRatePerBtc.value;
        double amountAsDouble = amount != null ? (double) amount.value : 0;
        double btcAsDouble = (double) Coin.COIN.value;
        double fact = amountAsDouble / btcAsDouble;
        Coin feePerBtc = Coin.valueOf(Math.round(feePerBtcAsDouble * fact));
        Coin minFee = daoStateService.getParamValueAsCoin(minFeeParam, minFeeParam.getDefaultValue());
        return maxCoin(feePerBtc, minFee);
    }

    private Coin getMakerFeeRateBsq(long blockHeight) {
        return daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BSQ, (int) blockHeight);
    }

    private Coin getTakerFeeRateBsq(long blockHeight) {
        return daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BSQ, (int) blockHeight);
    }

    private Coin getMakerFeeRateBtc(long blockHeight) {
        return daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BTC, (int) blockHeight);
    }

    private Coin getTakerFeeRateBtc(long blockHeight) {
        return daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BTC, (int) blockHeight);
    }

    private Optional<Boolean> maybeCheckAgainstFeeFromFilter(Coin tradeAmount,
                                                             boolean isMaker,
                                                             Coin feeValueAsCoin,
                                                             Param minFeeParam,
                                                             boolean isBtcFee,
                                                             String description) {
        if (new Date().before(USE_FEE_FROM_FILTER_ACTIVATION_DATE)) {
            return Optional.empty();
        }
        return getFeeFromFilter(isMaker, isBtcFee)
                .map(feeFromFilter -> {
                    boolean isValid = testWithFeeFromFilter(tradeAmount, feeValueAsCoin, feeFromFilter, minFeeParam);
                    if (!isValid) {
                        log.warn("Fee does not match fee from filter. Fee from filter={}. {}", feeFromFilter, description);
                    }
                    return isValid;
                });
    }

    private Optional<Coin> getFeeFromFilter(boolean isMaker, boolean isBtcFee) {
        return Optional.ofNullable(filterManager.getFilter())
                .map(filter -> {
                    Coin value;
                    if (isMaker) {
                        value = isBtcFee ?
                                Coin.valueOf(filter.getMakerFeeBtc()) :
                                Coin.valueOf(filter.getMakerFeeBsq());
                    } else {
                        value = isBtcFee ?
                                Coin.valueOf(filter.getTakerFeeBtc()) :
                                Coin.valueOf(filter.getTakerFeeBsq());
                    }
                    return value;
                })
                .filter(Coin::isPositive);
    }

    private boolean testWithFeeFromFilter(Coin tradeAmount,
                                          Coin actualFeeValue,
                                          Coin feeFromFilter,
                                          Param minFeeParam) {
        long actualFeeAsLong = actualFeeValue.value;
        long feeFromFilterAsLong = calculateFee(tradeAmount, feeFromFilter, minFeeParam).value;
        double deviation = actualFeeAsLong / (double) feeFromFilterAsLong;
        // It can be that the filter has not been updated immediately after DAO param change, so we need a tolerance
        // Common change rate is 15-20%
        return deviation > 0.7;
    }

    // implements leniency rule of accepting old DAO rate parameters: https://github.com/bisq-network/bisq/issues/5329#issuecomment-803223859
    // We iterate over all past dao param values and if one of those matches we consider it valid. That covers the non-in-sync cases.
    private boolean feeExistsUsingDifferentDaoParam(Coin tradeAmount,
                                                    Coin actualFeeValue,
                                                    Param defaultFeeParam,
                                                    Param minFeeParam) {
        for (Coin daoHistoricalRate : daoStateService.getParamChangeList(defaultFeeParam)) {
            if (actualFeeValue.equals(calculateFee(tradeAmount, daoHistoricalRate, minFeeParam))) {
                return true;
            }
        }
        // Finally, check the default rate used when we ask for the fee rate at genesis block height (it is hard coded in the Param enum)
        Coin defaultRate = daoStateService.getParamValueAsCoin(defaultFeeParam, daoStateService.getGenesisBlockHeight());
        return actualFeeValue.equals(calculateFee(tradeAmount, defaultRate, minFeeParam));
    }

    public TxValidator endResult(String title, boolean status) {
        log.info("{} : {}", title, status ? "SUCCESS" : "FAIL");
        if (!status) {
            errorList.add(title);
        }
        return this;
    }

    public boolean isFail() {
        return errorList.size() > 0;
    }

    public boolean getResult() {
        return errorList.size() == 0;
    }

    public String errorSummary() {
        return errorList.toString().substring(0, Math.min(85, errorList.toString().length()));
    }

    public String toString() {
        return errorList.toString();
    }
}
