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

import bisq.proto.grpc.TradeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static bisq.cli.table.builder.TableType.TRADE_DETAIL_TBL;
import static java.lang.String.format;
import static protobuf.BsqSwapTrade.State.COMPLETED;
import static protobuf.BsqSwapTrade.State.PREPARATION;



import bisq.cli.table.Table;
import bisq.cli.table.column.Column;

/**
 * Builds a {@code bisq.cli.table.Table} from a {@code bisq.proto.grpc.TradeInfo} object.
 */
@SuppressWarnings("ConstantConditions")
class TradeDetailTableBuilder extends AbstractTradeListBuilder {

    private final Predicate<TradeInfo> isPendingBsqSwap = (t) -> t.getState().equals(PREPARATION.name());
    private final Predicate<TradeInfo> isCompletedBsqSwap = (t) -> t.getState().equals(COMPLETED.name());

    TradeDetailTableBuilder(List<?> protos) {
        super(TRADE_DETAIL_TBL, protos);
    }

    /**
     * Build a single row trade detail table.
     * @return Table containing one row
     */
    public Table build() {
        // A trade detail table only has one row.
        var trade = trades.get(0);
        populateColumns(trade);
        List<Column<?>> columns = defineColumnList(trade);
        return new Table(columns.toArray(new Column<?>[0]));
    }

    private void populateColumns(TradeInfo trade) {
        if (isBsqSwapTrade.test(trade)) {
            var isPending = isPendingBsqSwap.test(trade);
            var isCompleted = isCompletedBsqSwap.test(trade);
            if (isPending == isCompleted)
                throw new IllegalStateException(
                        format("programmer error: trade must be either pending or completed, is pending=%s and completed=%s",
                                isPending,
                                isCompleted));
            populateBsqSwapTradeColumns(trade);
        } else {
            populateBisqV1TradeColumns(trade);
        }
    }

    private void populateBisqV1TradeColumns(TradeInfo trade) {
        colTradeId.addRow(trade.getShortId());
        colRole.addRow(trade.getRole());
        colPrice.addRow(trade.getTradePrice());
        colAmountInBtc.addRow(toAmount.apply(trade));
        colMinerTxFee.addRow(toMyMinerTxFee.apply(trade));
        colBisqTradeFee.addRow(toMyMakerOrTakerFee.apply(trade));
        colIsDepositPublished.addRow(trade.getIsDepositPublished());
        colIsDepositConfirmed.addRow(trade.getIsDepositConfirmed());
        colTradeCost.addRow(toTradeVolume.apply(trade));
        colIsPaymentSent.addRow(trade.getIsFiatSent());
        colIsPaymentReceived.addRow(trade.getIsFiatReceived());
        colIsPayoutPublished.addRow(trade.getIsPayoutPublished());
        colIsFundsWithdrawn.addRow(trade.getIsWithdrawn());
        if (colAltcoinReceiveAddressColumn != null)
            colAltcoinReceiveAddressColumn.addRow(toAltcoinReceiveAddress.apply(trade));
    }

    private void populateBsqSwapTradeColumns(TradeInfo trade) {
        colTradeId.addRow(trade.getShortId());
        colRole.addRow(trade.getRole());
        colPrice.addRow(trade.getTradePrice());
        colAmountInBtc.addRow(toAmount.apply(trade));
        colMinerTxFee.addRow(toMyMinerTxFee.apply(trade));
        colBisqTradeFee.addRow(toMyMakerOrTakerFee.apply(trade));
        colTradeCost.addRow(toTradeVolume.apply(trade));

        var isCompleted = isCompletedBsqSwap.test(trade);
        status.addRow(isCompleted ? "COMPLETED" : "PENDING");
        if (isCompleted) {
            colTxId.addRow(trade.getBsqSwapTradeInfo().getTxId());
            colNumConfirmations.addRow(trade.getBsqSwapTradeInfo().getNumConfirmations());
        }
    }

    private List<Column<?>> defineColumnList(TradeInfo trade) {
        return isBsqSwapTrade.test(trade)
                ? getBsqSwapTradeColumnList(isCompletedBsqSwap.test(trade))
                : getBisqV1TradeColumnList();
    }

    private List<Column<?>> getBisqV1TradeColumnList() {
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

    private List<Column<?>> getBsqSwapTradeColumnList(boolean isCompleted) {
        List<Column<?>> columns = new ArrayList<>() {{
            add(colTradeId);
            add(colRole);
            add(colPrice.asStringColumn());
            add(colAmountInBtc.asStringColumn());
            add(colMinerTxFee.asStringColumn());
            add(colBisqTradeFee.asStringColumn());
            add(colTradeCost.asStringColumn());
            add(status);
        }};

        if (isCompleted)
            columns.add(colTxId);

        if (!colNumConfirmations.isEmpty())
            columns.add(colNumConfirmations.asStringColumn());

        return columns;
    }
}
