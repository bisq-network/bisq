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

package bisq.cli.table.builder;

import bisq.proto.grpc.ContractInfo;
import bisq.proto.grpc.TradeInfo;

import java.text.DecimalFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_BUYER_DEPOSIT;
import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_SELLER_DEPOSIT;
import static bisq.cli.table.builder.TableType.TRADE_DETAIL_TBL;
import static protobuf.OfferDirection.SELL;



import bisq.cli.table.column.Column;
import bisq.cli.table.column.MixedTradeFeeColumn;
import bisq.cli.table.column.MixedVolumeColumn;

abstract class AbstractTradeListBuilder extends AbstractTableBuilder {

    protected final List<TradeInfo> trades;

    protected final TradeTableColumnSupplier colSupplier;

    protected final Column<String> colTradeId;
    @Nullable
    protected final Column<Long> colCreateDate;
    @Nullable
    protected final Column<String> colMarket;
    protected final Column<Long> colPrice;
    @Nullable
    protected final Column<String> colPriceDeviation;
    @Nullable
    protected final Column<String> colCurrency;
    @Nullable
    protected final Column<Long> colAmountInBtc;
    @Nullable
    protected final MixedVolumeColumn colMixedAmount;
    @Nullable
    protected final Column<Long> colMinerTxFee;
    @Nullable
    protected final MixedTradeFeeColumn colMixedTradeFee;
    @Nullable
    protected final Column<Long> colBuyerDeposit;
    @Nullable
    protected final Column<Long> colSellerDeposit;
    @Nullable
    protected final Column<String> colPaymentMethod;
    @Nullable
    protected final Column<String> colRole;
    @Nullable
    protected final Column<String> colOfferType;
    @Nullable
    protected final Column<String> colClosingStatus;

    // Trade detail tbl specific columns

    @Nullable
    protected final Column<Boolean> colIsDepositPublished;
    @Nullable
    protected final Column<Boolean> colIsDepositConfirmed;
    @Nullable
    protected final Column<Boolean> colIsPayoutPublished;
    @Nullable
    protected final Column<Boolean> colIsFundsWithdrawn;
    @Nullable
    protected final Column<Long> colBisqTradeFee;
    @Nullable
    protected final Column<Long> colTradeCost;
    @Nullable
    protected final Column<Boolean> colIsPaymentSent;
    @Nullable
    protected final Column<Boolean> colIsPaymentReceived;
    @Nullable
    protected final Column<String> colAltcoinReceiveAddressColumn;

    // BSQ swap trade detail specific columns

    @Nullable
    protected final Column<String> status;
    @Nullable
    protected final Column<String> colTxId;
    @Nullable
    protected final Column<Long> colNumConfirmations;

    AbstractTradeListBuilder(TableType tableType, List<?> protos) {
        super(tableType, protos);
        validate();

        this.trades = protos.stream().map(p -> (TradeInfo) p).collect(Collectors.toList());
        this.colSupplier = new TradeTableColumnSupplier(tableType, trades);

        this.colTradeId = colSupplier.tradeIdColumn.get();
        this.colCreateDate = colSupplier.createDateColumn.get();
        this.colMarket = colSupplier.marketColumn.get();
        this.colPrice = colSupplier.priceColumn.get();
        this.colPriceDeviation = colSupplier.priceDeviationColumn.get();
        this.colCurrency = colSupplier.currencyColumn.get();
        this.colAmountInBtc = colSupplier.amountInBtcColumn.get();
        this.colMixedAmount = colSupplier.mixedAmountColumn.get();
        this.colMinerTxFee = colSupplier.minerTxFeeColumn.get();
        this.colMixedTradeFee = colSupplier.mixedTradeFeeColumn.get();
        this.colBuyerDeposit = colSupplier.toSecurityDepositColumn.apply(COL_HEADER_BUYER_DEPOSIT);
        this.colSellerDeposit = colSupplier.toSecurityDepositColumn.apply(COL_HEADER_SELLER_DEPOSIT);
        this.colPaymentMethod = colSupplier.paymentMethodColumn.get();
        this.colRole = colSupplier.roleColumn.get();
        this.colOfferType = colSupplier.offerTypeColumn.get();
        this.colClosingStatus = colSupplier.statusDescriptionColumn.get();

        // Trade detail specific columns, some in common with BSQ swap trades detail.

        this.colIsDepositPublished = colSupplier.depositPublishedColumn.get();
        this.colIsDepositConfirmed = colSupplier.depositConfirmedColumn.get();
        this.colIsPayoutPublished = colSupplier.payoutPublishedColumn.get();
        this.colIsFundsWithdrawn = colSupplier.fundsWithdrawnColumn.get();
        this.colBisqTradeFee = colSupplier.bisqTradeDetailFeeColumn.get();
        this.colTradeCost = colSupplier.tradeCostColumn.get();
        this.colIsPaymentSent = colSupplier.paymentSentColumn.get();
        this.colIsPaymentReceived = colSupplier.paymentReceivedColumn.get();
        this.colAltcoinReceiveAddressColumn = colSupplier.altcoinReceiveAddressColumn.get();

        // BSQ swap trade detail specific columns

        this.status = colSupplier.bsqSwapStatusColumn.get();
        this.colTxId = colSupplier.bsqSwapTxIdColumn.get();
        this.colNumConfirmations = colSupplier.numConfirmationsColumn.get();
    }

    protected void validate() {
        if (isTradeDetailTblBuilder.get()) {
            if (protos.size() != 1)
                throw new IllegalArgumentException("trade detail tbl can have only one row");
        } else if (protos.isEmpty()) {
            throw new IllegalArgumentException("trade tbl has no rows");
        }
    }

    // Helper Functions

    private final Supplier<Boolean> isTradeDetailTblBuilder = () -> tableType.equals(TRADE_DETAIL_TBL);
    protected final Predicate<TradeInfo> isFiatTrade = (t) -> isFiatOffer.test(t.getOffer());
    protected final Predicate<TradeInfo> isBsqSwapTrade = (t) -> t.getOffer().getIsBsqSwapOffer();
    protected final Predicate<TradeInfo> isMyOffer = (t) -> t.getOffer().getIsMyOffer();
    protected final Predicate<TradeInfo> isTaker = (t) -> t.getRole().toLowerCase().contains("taker");
    protected final Predicate<TradeInfo> isSellOffer = (t) -> t.getOffer().getDirection().equals(SELL.name());
    protected final Predicate<TradeInfo> isBtcSeller = (t) -> (isMyOffer.test(t) && isSellOffer.test(t))
            || (!isMyOffer.test(t) && !isSellOffer.test(t));
    protected final Predicate<TradeInfo> isTradeFeeBtc = (t) -> isMyOffer.test(t)
            ? t.getOffer().getIsCurrencyForMakerFeeBtc()
            : t.getIsCurrencyForTakerFeeBtc();


    // Column Value Functions

    protected final Function<TradeInfo, Long> toAmount = (t) ->
            isFiatTrade.test(t)
                    ? t.getTradeAmountAsLong()
                    : t.getTradeVolume();

    protected final Function<TradeInfo, Long> toTradeVolume = (t) ->
            isFiatTrade.test(t)
                    ? t.getTradeVolume()
                    : t.getTradeAmountAsLong();

    protected final Function<TradeInfo, String> toMarket = (t) ->
            t.getOffer().getBaseCurrencyCode() + "/"
                    + t.getOffer().getCounterCurrencyCode();

    protected final Function<TradeInfo, String> toPaymentCurrencyCode = (t) ->
            isFiatTrade.test(t)
                    ? t.getOffer().getCounterCurrencyCode()
                    : t.getOffer().getBaseCurrencyCode();


    protected final Function<TradeInfo, Integer> toDisplayedVolumePrecision = (t) -> {
        if (isFiatTrade.test(t)) {
            return 0;
        } else {
            String currencyCode = toPaymentCurrencyCode.apply(t);
            return currencyCode.equalsIgnoreCase("BSQ") ? 2 : 8;
        }
    };

    protected final Function<TradeInfo, String> toPriceDeviation = (t) ->
            t.getOffer().getUseMarketBasedPrice()
                    ? formatToPercent(t.getOffer().getMarketPriceMargin())
                    : "N/A";

    protected final Function<TradeInfo, Long> toMyMinerTxFee = (t) -> {
        if (isBsqSwapTrade.test(t)) {
            // The BTC seller pays the miner fee for both sides.
            return isBtcSeller.test(t) ? t.getTxFeeAsLong() : 0L;
        } else {
            return isTaker.test(t)
                    ? t.getTxFeeAsLong()
                    : t.getOffer().getTxFee();
        }
    };

    protected final Function<TradeInfo, Long> toTradeFeeBsq = (t) -> {
        var isMyOffer = t.getOffer().getIsMyOffer();
        if (isMyOffer) {
            return t.getOffer().getIsCurrencyForMakerFeeBtc()
                    ? 0L // Maker paid BTC fee, return 0.
                    : t.getOffer().getMakerFee();
        } else {
            return t.getIsCurrencyForTakerFeeBtc()
                    ? 0L // Taker paid BTC fee, return 0.
                    : t.getTakerFeeAsLong();
        }
    };

    protected final Function<TradeInfo, Long> toTradeFeeBtc = (t) -> {
        var isMyOffer = t.getOffer().getIsMyOffer();
        if (isMyOffer) {
            return t.getOffer().getIsCurrencyForMakerFeeBtc()
                    ? t.getOffer().getMakerFee()
                    : 0L;  // Maker paid BSQ fee, return 0.
        } else {
            return t.getIsCurrencyForTakerFeeBtc()
                    ? t.getTakerFeeAsLong()
                    : 0L; // Taker paid BSQ fee, return 0.
        }
    };

    protected final Function<TradeInfo, Long> toMyMakerOrTakerFee = (t) -> {
        if (isBsqSwapTrade.test(t)) {
            return isTaker.test(t)
                    ? t.getBsqSwapTradeInfo().getBsqTakerTradeFee()
                    : t.getBsqSwapTradeInfo().getBsqMakerTradeFee();
        } else {
            return isTaker.test(t)
                    ? t.getTakerFeeAsLong()
                    : t.getOffer().getMakerFee();
        }
    };

    protected final Function<TradeInfo, String> toOfferType = (t) -> {
        if (isFiatTrade.test(t)) {
            return t.getOffer().getDirection() + " " + t.getOffer().getBaseCurrencyCode();
        } else {
            if (t.getOffer().getDirection().equals("BUY")) {
                return "SELL " + t.getOffer().getBaseCurrencyCode();
            } else {
                return "BUY " + t.getOffer().getBaseCurrencyCode();
            }
        }
    };

    protected final Predicate<TradeInfo> showAltCoinBuyerAddress = (t) -> {
        if (isFiatTrade.test(t)) {
            return false;
        } else {
            ContractInfo contract = t.getContract();
            boolean isBuyerMakerAndSellerTaker = contract.getIsBuyerMakerAndSellerTaker();
            if (isTaker.test(t)) {
                return !isBuyerMakerAndSellerTaker;
            } else {
                return isBuyerMakerAndSellerTaker;
            }
        }
    };

    protected final Function<TradeInfo, String> toAltcoinReceiveAddress = (t) -> {
        if (showAltCoinBuyerAddress.test(t)) {
            ContractInfo contract = t.getContract();
            boolean isBuyerMakerAndSellerTaker = contract.getIsBuyerMakerAndSellerTaker();
            return isBuyerMakerAndSellerTaker  // (is BTC buyer / maker)
                    ? contract.getTakerPaymentAccountPayload().getAddress()
                    : contract.getMakerPaymentAccountPayload().getAddress();
        } else {
            return "";
        }
    };

    // TODO Move to bisq/cli/CurrencyFormat.java ?

    public static String formatToPercent(double value) {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        decimalFormat.setMinimumFractionDigits(2);
        decimalFormat.setMaximumFractionDigits(2);
        return formatToPercent(value, decimalFormat);
    }

    public static String formatToPercent(double value, DecimalFormat decimalFormat) {
        return decimalFormat.format(roundDouble(value * 100.0, 2)).replace(",", ".") + "%";
    }

    public static double roundDouble(double value, int precision) {
        return roundDouble(value, precision, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("SameParameterValue")
    public static double roundDouble(double value, int precision, RoundingMode roundingMode) {
        if (precision < 0)
            throw new IllegalArgumentException();
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Expected a finite double, but found " + value);

        try {
            BigDecimal bd = BigDecimal.valueOf(value);
            bd = bd.setScale(precision, roundingMode);
            return bd.doubleValue();
        } catch (Throwable t) {
            t.printStackTrace(); // TODO throw pretty exception for CLI console
            return 0;
        }
    }
}
