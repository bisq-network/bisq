package bisq.cli.table.builder;

import java.util.List;

import static bisq.cli.table.builder.TableType.CLOSED_TRADES_TBL;



import bisq.cli.table.Table;
import bisq.cli.table.column.MixedPriceColumn;

class ClosedTradeTableBuilder extends AbstractTradeListBuilder {

    ClosedTradeTableBuilder(List<?> protos) {
        super(CLOSED_TRADES_TBL, protos);
    }

    public Table build() {
        populateColumns();
        return new Table(colTradeId,
                colCreateDate.asStringColumn(),
                colMarket,
                colPrice.asStringColumn(),
                colPriceDeviation.justify(),
                colAmountInBtc.asStringColumn(),
                colMixedAmount.asStringColumn(),
                colCurrency,
                colMinerTxFee.asStringColumn(),
                colMixedTradeFee.asStringColumn(),
                colBuyerDeposit.asStringColumn(),
                colSellerDeposit.asStringColumn(),
                colOfferType,
                colClosingStatus);
    }

    private void populateColumns() {
        trades.stream().forEachOrdered(t -> {
            colTradeId.addRow(t.getTradeId());
            colCreateDate.addRow(t.getDate());
            colMarket.addRow(toMarket.apply(t));
            ((MixedPriceColumn) colPrice).addRow(t.getTradePrice(), isFiatTrade.test(t));
            colPriceDeviation.addRow(toPriceDeviation.apply(t));
            colAmountInBtc.addRow(t.getTradeAmountAsLong());
            colMixedAmount.addRow(t.getTradeVolume(), toDisplayedVolumePrecision.apply(t));
            colCurrency.addRow(toPaymentCurrencyCode.apply(t));
            colMinerTxFee.addRow(toMyMinerTxFee.apply(t));

            if (t.getOffer().getIsBsqSwapOffer()) {
                // For BSQ Swaps, BTC buyer pays the BSQ trade fee for both sides (BTC seller pays no fee).
                var optionalTradeFeeBsq = isBtcSeller.test(t) ? 0L : toTradeFeeBsq.apply(t);
                colMixedTradeFee.addRow(optionalTradeFeeBsq, true);
            } else if (isTradeFeeBtc.test(t)) {
                colMixedTradeFee.addRow(toTradeFeeBsq.apply(t), false);
            } else {
                // V1 trade fee paid in BSQ.
                colMixedTradeFee.addRow(toTradeFeeBsq.apply(t), true);
            }

            colBuyerDeposit.addRow(t.getOffer().getBuyerSecurityDeposit());
            colSellerDeposit.addRow(t.getOffer().getSellerSecurityDeposit());
            colOfferType.addRow(toOfferType.apply(t));
            colClosingStatus.addRow(t.getClosingStatus());
        });
    }
}
