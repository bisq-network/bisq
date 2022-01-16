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
                colStatusDescription);
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

            long tradeFeeBsq = toTradeFeeBsq.apply(t, true);
            long tradeFeeBtc = toTradeFeeBtc.apply(t, true);
            if (tradeFeeBsq > 0)
                colMixedTradeFee.addRow(tradeFeeBsq, true);
            else
                colMixedTradeFee.addRow(tradeFeeBtc, false);

            colBuyerDeposit.addRow(t.getBuyerDeposit());
            colSellerDeposit.addRow(t.getSellerDeposit());
            colOfferType.addRow(toOfferType.apply(t));
            colStatusDescription.addRow(t.getStatusDescription());
        });
    }
}
