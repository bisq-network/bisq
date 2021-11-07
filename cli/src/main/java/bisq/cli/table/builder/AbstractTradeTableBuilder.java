package bisq.cli.table.builder;

import bisq.proto.grpc.TradeInfo;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import static bisq.cli.table.builder.TableBuilderConstants.*;
import static bisq.cli.table.builder.TableType.TRADE_DETAIL_TBL;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;



import bisq.cli.table.column.BtcColumn;
import bisq.cli.table.column.Column;
import bisq.cli.table.column.Iso8601DateTimeColumn;
import bisq.cli.table.column.MixedPriceColumn;
import bisq.cli.table.column.MixedTradeFeeColumn;
import bisq.cli.table.column.MixedVolumeColumn;
import bisq.cli.table.column.SatoshiColumn;
import bisq.cli.table.column.StringColumn;

/**
 * Builds a {@code bisq.cli.table.Table} from one or more {@code bisq.proto.grpc.TradeInfo} objects.
 */
abstract class AbstractTradeTableBuilder extends AbstractTableBuilder {

    @Nullable
    protected final Column<String> colTradeId;
    @Nullable
    protected final Column<Long> colCreateDate;
    @Nullable
    protected final Column<String> colMarket;
    @Nullable
    protected final MixedPriceColumn colMixedPrice;
    @Nullable
    protected final Column<String> colPriceDeviation;
    @Nullable
    protected final Column<Long> colAmountInBtc;
    @Nullable
    protected final MixedVolumeColumn colMixedAmount;
    @Nullable
    protected final Column<String> colCurrency;
    @Nullable
    protected final MixedTradeFeeColumn colMixedTradeFee;
    @Nullable
    protected final Column<Long> colBuyerDeposit;
    @Nullable
    protected final Column<Long> colSellerDeposit;
    @Nullable
    protected final Column<String> colOfferType;
    @Nullable
    protected final Column<String> colStatus;
    protected final Column<Long> colMinerTxFee;

    AbstractTradeTableBuilder(TableType tableType, List<?> protos) {
        super(tableType, protos);
        boolean isTradeDetail = tableType.equals(TRADE_DETAIL_TBL);
        this.colTradeId = isTradeDetail ? null : new StringColumn(COL_HEADER_TRADE_ID);
        this.colCreateDate = isTradeDetail ? null : new Iso8601DateTimeColumn(COL_HEADER_DATE_TIME);
        this.colMarket = isTradeDetail ? null : new StringColumn(COL_HEADER_MARKET);
        this.colMixedPrice = isTradeDetail ? null : new MixedPriceColumn(COL_HEADER_PRICE);
        this.colPriceDeviation = isTradeDetail ? null : new StringColumn(COL_HEADER_DEVIATION, RIGHT);
        this.colAmountInBtc = isTradeDetail ? null : new BtcColumn(COL_HEADER_AMOUNT_IN_BTC);
        this.colMixedAmount = isTradeDetail ? null : new MixedVolumeColumn(COL_HEADER_AMOUNT);
        this.colCurrency = isTradeDetail ? null : new StringColumn(COL_HEADER_CURRENCY);
        this.colMixedTradeFee = isTradeDetail ? null : new MixedTradeFeeColumn(COL_HEADER_TRADE_FEE);
        this.colBuyerDeposit = isTradeDetail ? null : new SatoshiColumn(COL_HEADER_BUYER_DEPOSIT);
        this.colSellerDeposit = isTradeDetail ? null : new SatoshiColumn(COL_HEADER_SELLER_DEPOSIT);
        this.colOfferType = isTradeDetail ? null : new StringColumn(COL_HEADER_OFFER_TYPE);
        this.colStatus = isTradeDetail ? null : new StringColumn(COL_HEADER_STATUS);
        this.colMinerTxFee = new SatoshiColumn(COL_HEADER_TX_FEE);
    }

    protected final Predicate<TradeInfo> isFiatTrade = (t) -> isFiatOffer.test(t.getOffer());

    protected final Predicate<TradeInfo> isTaker = (t) -> t.getRole().toLowerCase().contains("taker");

    protected final Function<TradeInfo, String> toPaymentCurrencyCode = (t) ->
            isFiatTrade.test(t)
                    ? t.getOffer().getCounterCurrencyCode()
                    : t.getOffer().getBaseCurrencyCode();

    protected final Function<TradeInfo, Long> toAmount = (t) ->
            isFiatTrade.test(t)
                    ? t.getTradeAmountAsLong()
                    : t.getTradeVolume();

    protected final Function<TradeInfo, Long> toMinerTxFee = (t) ->
            isTaker.test(t)
                    ? t.getTxFeeAsLong()
                    : t.getOffer().getTxFee();

    protected final Function<TradeInfo, Long> toMakerTakerFee = (t) ->
            isTaker.test(t)
                    ? t.getTakerFeeAsLong()
                    : t.getOffer().getMakerFee();

    protected final Function<TradeInfo, Long> toTradeCost = (t) ->
            isFiatTrade.test(t)
                    ? t.getTradeVolume()
                    : t.getTradeAmountAsLong();
}
