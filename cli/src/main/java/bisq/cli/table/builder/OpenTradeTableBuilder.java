package bisq.cli.table.builder;

import java.util.List;

import static bisq.cli.table.builder.TableType.OPEN_TRADES_TBL;



import bisq.cli.table.Table;
import bisq.cli.table.column.MixedPriceColumn;

/**
 * Builds a {@code bisq.cli.table.Table} from a list of {@code bisq.proto.grpc.TradeInfo} objects.
 */
class OpenTradeTableBuilder extends AbstractTradeListBuilder {

    OpenTradeTableBuilder(List<?> protos) {
        super(OPEN_TRADES_TBL, protos);
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
                colPaymentMethod,
                colRole);
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
            colPaymentMethod.addRow(t.getOffer().getPaymentMethodShortName());
            colRole.addRow(t.getRole());
        });
    }
}
