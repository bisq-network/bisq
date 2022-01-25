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
                colMixedTradeFee.addRow(toTradeFeeBtc.apply(t), false);
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
