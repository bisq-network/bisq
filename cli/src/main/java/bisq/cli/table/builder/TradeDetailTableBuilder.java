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

import java.util.ArrayList;
import java.util.List;

import static bisq.cli.table.builder.TableType.TRADE_DETAIL_TBL;



import bisq.cli.table.Table;
import bisq.cli.table.column.Column;

/**
 * Builds a {@code bisq.cli.table.Table} from a {@code bisq.proto.grpc.TradeInfo} object.
 */
class TradeDetailTableBuilder extends AbstractTradeListBuilder {

    TradeDetailTableBuilder(List<?> protos) {
        super(TRADE_DETAIL_TBL, protos);
    }

    /**
     * Build a single row trade detail table.
     * @return Table containing one row
     */
    public Table build() {
        populateColumns();
        List<Column<?>> columns = defineColumnList();
        return new Table(columns.toArray(new Column<?>[0]));
    }

    private void populateColumns() {
        trades.stream().forEachOrdered(t -> {
            colTradeId.addRow(t.getShortId());
            colRole.addRow(t.getRole());
            colPrice.addRow(t.getTradePrice());
            colAmountInBtc.addRow(toAmount.apply(t));
            colMinerTxFee.addRow(toMyMinerTxFee.apply(t));
            colBisqTradeFee.addRow(toMyMakerOrTakerFee.apply(t));
            colIsDepositPublished.addRow(t.getIsDepositPublished());
            colIsDepositConfirmed.addRow(t.getIsDepositConfirmed());
            colTradeCost.addRow(toTradeVolume.apply(t));
            colIsPaymentSent.addRow(t.getIsFiatSent());
            colIsPaymentReceived.addRow(t.getIsFiatReceived());
            colIsPayoutPublished.addRow(t.getIsPayoutPublished());
            colIsFundsWithdrawn.addRow(t.getIsWithdrawn());

            if (colAltcoinReceiveAddressColumn != null)
                colAltcoinReceiveAddressColumn.addRow(toAltcoinReceiveAddress.apply(t));
        });
    }

    private List<Column<?>> defineColumnList() {
        List<Column<?>> columns = new ArrayList<>() {{
            add(colTradeId);
            add(colRole);
            add(colPrice.asStringColumn());
            add(colAmountInBtc.asStringColumn());
            add(colMinerTxFee.asStringColumn());
            add(colBisqTradeFee.asStringColumn());
            add(colIsDepositPublished.asStringColumn());
            add(colIsDepositConfirmed.asStringColumn());
            add(colTradeCost.asStringColumn());
            add(colIsPaymentSent.asStringColumn());
            add(colIsPaymentReceived.asStringColumn());
            add(colIsPayoutPublished.asStringColumn());
            add(colIsFundsWithdrawn.asStringColumn());
        }};

        if (colAltcoinReceiveAddressColumn != null)
            columns.add(colAltcoinReceiveAddressColumn);

        return columns;
    }
}
