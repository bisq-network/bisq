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
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.cli.table.builder.TableBuilderConstants.*;
import static bisq.cli.table.builder.TableType.CLOSED_TRADES_TBL;
import static bisq.cli.table.builder.TableType.FAILED_TRADES_TBL;
import static bisq.cli.table.builder.TableType.OPEN_TRADES_TBL;
import static bisq.cli.table.builder.TableType.TRADE_DETAIL_TBL;
import static bisq.cli.table.column.AltcoinColumn.DISPLAY_MODE.ALTCOIN_OFFER_VOLUME;
import static bisq.cli.table.column.Column.JUSTIFICATION.LEFT;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;
import static bisq.cli.table.column.FiatColumn.DISPLAY_MODE.VOLUME;
import static java.lang.String.format;



import bisq.cli.table.column.AltcoinColumn;
import bisq.cli.table.column.BooleanColumn;
import bisq.cli.table.column.BtcColumn;
import bisq.cli.table.column.Column;
import bisq.cli.table.column.FiatColumn;
import bisq.cli.table.column.Iso8601DateTimeColumn;
import bisq.cli.table.column.LongColumn;
import bisq.cli.table.column.MixedPriceColumn;
import bisq.cli.table.column.MixedTradeFeeColumn;
import bisq.cli.table.column.MixedVolumeColumn;
import bisq.cli.table.column.SatoshiColumn;
import bisq.cli.table.column.StringColumn;

/**
 * Convenience for supplying column definitions to
 * open/closed/failed/detail trade table builders.
 */
@Slf4j
class TradeTableColumnSupplier {

    @Getter
    private final TableType tableType;
    @Getter
    private final List<TradeInfo> trades;

    public TradeTableColumnSupplier(TableType tableType, List<TradeInfo> trades) {
        this.tableType = tableType;
        this.trades = trades;
    }

    private final Supplier<Boolean> isTradeDetailTblBuilder = () -> getTableType().equals(TRADE_DETAIL_TBL);
    private final Supplier<Boolean> isOpenTradeTblBuilder = () -> getTableType().equals(OPEN_TRADES_TBL);
    private final Supplier<Boolean> isClosedTradeTblBuilder = () -> getTableType().equals(CLOSED_TRADES_TBL);
    private final Supplier<Boolean> isFailedTradeTblBuilder = () -> getTableType().equals(FAILED_TRADES_TBL);
    private final Supplier<TradeInfo> firstRow = () -> getTrades().get(0);
    private final Predicate<OfferInfo> isFiatOffer = (o) -> o.getBaseCurrencyCode().equals("BTC");
    private final Predicate<TradeInfo> isFiatTrade = (t) -> isFiatOffer.test(t.getOffer());
    private final Predicate<TradeInfo> isBsqSwapTrade = (t) -> t.getOffer().getIsBsqSwapOffer();
    private final Predicate<TradeInfo> isTaker = (t) -> t.getRole().toLowerCase().contains("taker");
    private final Supplier<Boolean> isSwapTradeDetail = () ->
            isTradeDetailTblBuilder.get() && isBsqSwapTrade.test(firstRow.get());

    final Supplier<StringColumn> tradeIdColumn = () -> isTradeDetailTblBuilder.get()
            ? new StringColumn(COL_HEADER_TRADE_SHORT_ID)
            : new StringColumn(COL_HEADER_TRADE_ID);

    final Supplier<Iso8601DateTimeColumn> createDateColumn = () -> isTradeDetailTblBuilder.get()
            ? null
            : new Iso8601DateTimeColumn(COL_HEADER_DATE_TIME);

    final Supplier<StringColumn> marketColumn = () -> isTradeDetailTblBuilder.get()
            ? null
            : new StringColumn(COL_HEADER_MARKET);

    private final Function<TradeInfo, Column<Long>> toDetailedPriceColumn = (t) -> {
        String colHeader = isFiatTrade.test(t)
                ? format(COL_HEADER_DETAILED_PRICE, t.getOffer().getCounterCurrencyCode())
                : format(COL_HEADER_DETAILED_PRICE_OF_ALTCOIN, t.getOffer().getBaseCurrencyCode());
        return isFiatTrade.test(t)
                ? new FiatColumn(colHeader)
                : new AltcoinColumn(colHeader);
    };

    final Supplier<Column<Long>> priceColumn = () -> isTradeDetailTblBuilder.get()
            ? toDetailedPriceColumn.apply(firstRow.get())
            : new MixedPriceColumn(COL_HEADER_PRICE);

    final Supplier<Column<String>> priceDeviationColumn = () -> isTradeDetailTblBuilder.get()
            ? null
            : new StringColumn(COL_HEADER_DEVIATION, RIGHT);

    final Supplier<StringColumn> currencyColumn = () -> isTradeDetailTblBuilder.get()
            ? null
            : new StringColumn(COL_HEADER_CURRENCY);

    private final Function<TradeInfo, Column<Long>> toDetailedAmountColumn = (t) -> {
        String headerCurrencyCode = t.getOffer().getBaseCurrencyCode();
        String colHeader = format(COL_HEADER_DETAILED_AMOUNT, headerCurrencyCode);
        return isFiatTrade.test(t)
                ? new SatoshiColumn(colHeader)
                : new AltcoinColumn(colHeader, ALTCOIN_OFFER_VOLUME);
    };

    final Supplier<Column<Long>> amountInBtcColumn = () -> isTradeDetailTblBuilder.get()
            ? toDetailedAmountColumn.apply(firstRow.get())
            : new BtcColumn(COL_HEADER_AMOUNT_IN_BTC);

    final Supplier<MixedVolumeColumn> mixedAmountColumn = () -> isTradeDetailTblBuilder.get()
            ? null
            : new MixedVolumeColumn(COL_HEADER_AMOUNT);

    final Supplier<Column<Long>> minerTxFeeColumn = () -> isTradeDetailTblBuilder.get() || isClosedTradeTblBuilder.get()
            ? new SatoshiColumn(COL_HEADER_TX_FEE)
            : null;

    final Supplier<MixedTradeFeeColumn> mixedTradeFeeColumn = () -> isTradeDetailTblBuilder.get()
            ? null
            : new MixedTradeFeeColumn(COL_HEADER_TRADE_FEE);

    final Supplier<StringColumn> paymentMethodColumn = () -> isTradeDetailTblBuilder.get() || isClosedTradeTblBuilder.get()
            ? null
            : new StringColumn(COL_HEADER_PAYMENT_METHOD, LEFT);

    final Supplier<StringColumn> roleColumn = () -> {
        if (isSwapTradeDetail.get())
            return new StringColumn(COL_HEADER_BSQ_SWAP_TRADE_ROLE);
        else
            return isTradeDetailTblBuilder.get() || isOpenTradeTblBuilder.get() || isFailedTradeTblBuilder.get()
                    ? new StringColumn(COL_HEADER_TRADE_ROLE)
                    : null;
    };

    final Function<String, Column<Long>> toSecurityDepositColumn = (name) -> isClosedTradeTblBuilder.get()
            ? new SatoshiColumn(name)
            : null;

    final Supplier<StringColumn> offerTypeColumn = () -> isTradeDetailTblBuilder.get()
            ? null
            : new StringColumn(COL_HEADER_OFFER_TYPE);

    final Supplier<StringColumn> statusDescriptionColumn = () -> isTradeDetailTblBuilder.get()
            ? null
            : new StringColumn(COL_HEADER_STATUS);

    private final Function<String, Column<Boolean>> toBooleanColumn = BooleanColumn::new;

    final Supplier<Column<Boolean>> depositPublishedColumn = () -> {
        if (isSwapTradeDetail.get())
            return null;
        else
            return isTradeDetailTblBuilder.get()
                    ? toBooleanColumn.apply(COL_HEADER_TRADE_DEPOSIT_PUBLISHED)
                    : null;
    };

    final Supplier<Column<Boolean>> depositConfirmedColumn = () -> {
        if (isSwapTradeDetail.get())
            return null;
        else
            return isTradeDetailTblBuilder.get()
                    ? toBooleanColumn.apply(COL_HEADER_TRADE_DEPOSIT_CONFIRMED)
                    : null;

    };

    final Supplier<Column<Boolean>> payoutPublishedColumn = () -> {
        if (isSwapTradeDetail.get())
            return null;
        else
            return isTradeDetailTblBuilder.get()
                    ? toBooleanColumn.apply(COL_HEADER_TRADE_PAYOUT_PUBLISHED)
                    : null;
    };

    final Supplier<Column<Boolean>> fundsWithdrawnColumn = () -> {
        if (isSwapTradeDetail.get())
            return null;
        else
            return isTradeDetailTblBuilder.get()
                    ? toBooleanColumn.apply(COL_HEADER_TRADE_WITHDRAWN)
                    : null;
    };

    final Supplier<Column<Long>> bisqTradeDetailFeeColumn = () -> {
        if (isTradeDetailTblBuilder.get()) {
            TradeInfo t = firstRow.get();
            String headerCurrencyCode = isTaker.test(t)
                    ? t.getIsCurrencyForTakerFeeBtc() ? "BTC" : "BSQ"
                    : t.getOffer().getIsCurrencyForMakerFeeBtc() ? "BTC" : "BSQ";
            String colHeader = isTaker.test(t)
                    ? format(COL_HEADER_TRADE_TAKER_FEE, headerCurrencyCode)
                    : format(COL_HEADER_TRADE_MAKER_FEE, headerCurrencyCode);
            boolean isBsqSatoshis = headerCurrencyCode.equals("BSQ");
            return new SatoshiColumn(colHeader, isBsqSatoshis);
        } else {
            return null;
        }
    };

    final Function<TradeInfo, String> toPaymentCurrencyCode = (t) ->
            isFiatTrade.test(t)
                    ? t.getOffer().getCounterCurrencyCode()
                    : t.getOffer().getBaseCurrencyCode();

    final Supplier<Column<Boolean>> paymentSentColumn = () -> {
        if (isTradeDetailTblBuilder.get()) {
            String headerCurrencyCode = toPaymentCurrencyCode.apply(firstRow.get());
            String colHeader = format(COL_HEADER_TRADE_PAYMENT_SENT, headerCurrencyCode);
            return new BooleanColumn(colHeader);
        } else {
            return null;
        }
    };

    final Supplier<Column<Boolean>> paymentReceivedColumn = () -> {
        if (isTradeDetailTblBuilder.get()) {
            String headerCurrencyCode = toPaymentCurrencyCode.apply(firstRow.get());
            String colHeader = format(COL_HEADER_TRADE_PAYMENT_RECEIVED, headerCurrencyCode);
            return new BooleanColumn(colHeader);
        } else {
            return null;
        }
    };

    final Supplier<Column<Long>> tradeCostColumn = () -> {
        if (isTradeDetailTblBuilder.get()) {
            TradeInfo t = firstRow.get();
            String headerCurrencyCode = t.getOffer().getCounterCurrencyCode();
            String colHeader = format(COL_HEADER_TRADE_BUYER_COST, headerCurrencyCode);
            return isFiatTrade.test(t)
                    ? new FiatColumn(colHeader, VOLUME)
                    : new SatoshiColumn(colHeader);
        } else {
            return null;
        }
    };

    final Supplier<Column<String>> bsqSwapTxIdColumn = () -> isSwapTradeDetail.get()
            ? new StringColumn(COL_HEADER_TX_ID)
            : null;

    final Supplier<Column<String>> bsqSwapStatusColumn = () -> isSwapTradeDetail.get()
            ? new StringColumn(COL_HEADER_STATUS)
            : null;

    final Supplier<Column<Long>> numConfirmationsColumn = () -> isSwapTradeDetail.get()
            ? new LongColumn(COL_HEADER_CONFIRMATIONS)
            : null;

    final Predicate<TradeInfo> showAltCoinBuyerAddress = (t) -> {
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

    @Nullable
    final Supplier<Column<String>> altcoinReceiveAddressColumn = () -> {
        if (isTradeDetailTblBuilder.get()) {
            TradeInfo t = firstRow.get();
            if (showAltCoinBuyerAddress.test(t)) {
                String headerCurrencyCode = toPaymentCurrencyCode.apply(t);
                String colHeader = format(COL_HEADER_TRADE_ALTCOIN_BUYER_ADDRESS, headerCurrencyCode);
                return new StringColumn(colHeader);
            } else {
                return null;
            }
        } else {
            return null;
        }
    };
}
