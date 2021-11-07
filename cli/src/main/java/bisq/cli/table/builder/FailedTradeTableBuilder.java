package bisq.cli.table.builder;

import java.util.List;

import static bisq.cli.table.builder.TableType.FAILED_TRADE_TBL;



import bisq.cli.table.Table;
import bisq.cli.table.column.MixedPriceColumn;

/**
 * Builds a {@code bisq.cli.table.Table} from a list of {@code bisq.proto.grpc.TradeInfo} objects.
 */
class FailedTradeTableBuilder extends AbstractTradeListBuilder {

    FailedTradeTableBuilder(List<?> protos) {
        super(FAILED_TRADE_TBL, protos);
    }

    public Table build() {
        populateColumns();
        return new Table(colTradeId,
                colCreateDate.asStringColumn(),
                colMarket,
                colPrice.asStringColumn(),
                colAmountInBtc.asStringColumn(),
                colMixedAmount.asStringColumn(),
                colCurrency,
                colOfferType,
                colRole,
                colStatusDescription);
    }

    private void populateColumns() {
        trades.stream().forEachOrdered(t -> {
            colTradeId.addRow(t.getTradeId());
            colCreateDate.addRow(t.getDate());
            colMarket.addRow(toMarket.apply(t));
            ((MixedPriceColumn) colPrice).addRow(t.getTradePrice(), isFiatTrade.test(t));
            colAmountInBtc.addRow(t.getTradeAmountAsLong());
            colMixedAmount.addRow(t.getTradeVolume(), toDisplayedVolumePrecision.apply(t));
            colCurrency.addRow(toPaymentCurrencyCode.apply(t));
            colOfferType.addRow(toOfferType.apply(t));
            colRole.addRow(t.getRole());
            colStatusDescription.addRow("Failed");
        });
    }
}
