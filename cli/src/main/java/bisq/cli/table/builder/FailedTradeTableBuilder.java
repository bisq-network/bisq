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

import java.util.List;

import static bisq.cli.table.builder.TableType.FAILED_TRADES_TBL;



import bisq.cli.table.Table;

/**
 * Builds a {@code bisq.cli.table.Table} from a list of {@code bisq.proto.grpc.TradeInfo} objects.
 */
@SuppressWarnings("ConstantConditions")
class FailedTradeTableBuilder extends AbstractTradeListBuilder {

    FailedTradeTableBuilder(List<?> protos) {
        super(FAILED_TRADES_TBL, protos);
    }

    public Table build() {
        populateColumns();
        return new Table(colTradeId,
                colCreateDate.asStringColumn(),
                colMarket,
                colPrice.justify(),
                colAmount.asStringColumn(),
                colMixedAmount.justify(),
                colCurrency,
                colOfferType,
                colRole,
                colClosingStatus);
    }

    private void populateColumns() {
        trades.forEach(t -> {
            colTradeId.addRow(t.getTradeId());
            colCreateDate.addRow(t.getDate());
            colMarket.addRow(toMarket.apply(t));
            colPrice.addRow(t.getTradePrice());
            colAmount.addRow(t.getTradeAmountAsLong());
            colMixedAmount.addRow(t.getTradeVolume());
            colCurrency.addRow(toPaymentCurrencyCode.apply(t));
            colOfferType.addRow(toOfferType.apply(t));
            colRole.addRow(t.getRole());
            colClosingStatus.addRow("Failed");
        });
    }
}
