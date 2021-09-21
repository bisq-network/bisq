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
import bisq.proto.grpc.TradeInfo;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import static bisq.cli.table.builder.TableBuilderConstants.*;
import static bisq.cli.table.builder.TableType.TRADE_TBL;
import static bisq.cli.table.column.AltcoinColumn.DISPLAY_MODE.ALTCOIN_OFFER_VOLUME;
import static bisq.cli.table.column.FiatColumn.DISPLAY_MODE.VOLUME;



import bisq.cli.table.Table;
import bisq.cli.table.column.AltcoinColumn;
import bisq.cli.table.column.BooleanColumn;
import bisq.cli.table.column.Column;
import bisq.cli.table.column.FiatColumn;
import bisq.cli.table.column.SatoshiColumn;
import bisq.cli.table.column.StringColumn;

/**
 * Builds a {@code bisq.cli.table.Table} from a {@code bisq.proto.grpc.TradeInfo} object.
 */
public class TradeTableBuilder extends AbstractTableBuilder {

    // Default columns not dynamically generated with trade info.
    private final Column<String> colShortId;
    private final Column<String> colRole;
    private final Column<Long> colMinerTxFee;
    private final Column<Boolean> colIsDepositPublished;
    private final Column<Boolean> colIsDepositConfirmed;
    private final Column<Boolean> colIsPayoutPublished;
    private final Column<Boolean> colIsFundsWithdrawn;

    public TradeTableBuilder(List<?> protos) {
        super(TRADE_TBL, protos);
        this.colShortId = new StringColumn(COL_HEADER_TRADE_SHORT_ID);
        this.colRole = new StringColumn(COL_HEADER_TRADE_ROLE);
        this.colMinerTxFee = new AltcoinColumn(COL_HEADER_TRADE_TX_FEE);
        this.colIsDepositPublished = new BooleanColumn(COL_HEADER_TRADE_DEPOSIT_PUBLISHED);
        this.colIsDepositConfirmed = new BooleanColumn(COL_HEADER_TRADE_DEPOSIT_CONFIRMED);
        this.colIsPayoutPublished = new BooleanColumn(COL_HEADER_TRADE_PAYOUT_PUBLISHED);
        this.colIsFundsWithdrawn = new BooleanColumn(COL_HEADER_TRADE_WITHDRAWN);
    }

    public Table build() {
        // TODO Add 'gettrades --currency --direction(?)' api method, & figure out how to
        //  show multiple trades in the console. For now, a trade tbl is only one row.

        TradeInfo trade = (TradeInfo) protos.get(0);

        // Declare the columns derived from trade info.

        Column<Long> colPrice = toPriceColumn.apply(trade);
        Column<Long> colAmount = toAmountColumn.apply(trade);
        Column<Long> colBisqTradeFee = toBisqTradeFeeColumn.apply(trade);
        Column<Long> tradeCostColumn = toTradeCostColumn.apply(trade);
        Column<Boolean> colIsPaymentSent = toPaymentSentColumn.apply(trade);
        Column<Boolean> colPaymentReceived = toPaymentReceivedColumn.apply(trade);
        @SuppressWarnings("ConstantConditions") @Nullable
        Column<String> colAltcoinReceiveAddressColumn = toAltcoinReceiveAddressColumn.apply(trade);

        // Populate columns with trade info.

        colShortId.addRow(trade.getShortId());
        colRole.addRow(trade.getRole());
        colPrice.addRow(trade.getTradePrice());
        colAmount.addRow(toAmount.apply(trade));
        colMinerTxFee.addRow(toMinerTxFee.apply(trade));
        colBisqTradeFee.addRow(toMakerTakerFee.apply(trade));
        colIsDepositPublished.addRow(trade.getIsDepositPublished());
        colIsDepositConfirmed.addRow(trade.getIsDepositConfirmed());
        tradeCostColumn.addRow(toTradeCost.apply(trade));
        colIsPaymentSent.addRow(trade.getIsFiatSent());
        colPaymentReceived.addRow(trade.getIsFiatReceived());
        colIsPayoutPublished.addRow(trade.getIsPayoutPublished());
        colIsFundsWithdrawn.addRow(trade.getIsWithdrawn());

        // Define and return the table instance with populated columns.

        if (colAltcoinReceiveAddressColumn != null) {
            colAltcoinReceiveAddressColumn.addRow(toAltcoinReceiveAddress.apply(trade));
            return new Table(colShortId,
                    colRole,
                    colPrice.asStringColumn(),
                    colAmount.asStringColumn(),
                    colMinerTxFee.asStringColumn(),
                    colBisqTradeFee.asStringColumn(),
                    colIsDepositPublished.asStringColumn(),
                    colIsDepositConfirmed.asStringColumn(),
                    tradeCostColumn.asStringColumn(),
                    colIsPaymentSent.asStringColumn(),
                    colPaymentReceived.asStringColumn(),
                    colIsPayoutPublished.asStringColumn(),
                    colIsFundsWithdrawn.asStringColumn(),
                    colAltcoinReceiveAddressColumn);
        } else {
            return new Table(colShortId,
                    colRole,
                    colPrice.asStringColumn(),
                    colAmount.asStringColumn(),
                    colMinerTxFee.asStringColumn(),
                    colBisqTradeFee.asStringColumn(),
                    colIsDepositPublished.asStringColumn(),
                    colIsDepositConfirmed.asStringColumn(),
                    tradeCostColumn.asStringColumn(),
                    colIsPaymentSent.asStringColumn(),
                    colPaymentReceived.asStringColumn(),
                    colIsPayoutPublished.asStringColumn(),
                    colIsFundsWithdrawn.asStringColumn());
        }
    }

    protected final Predicate<TradeInfo> isFiatTrade = (t) -> isFiatOffer.test(t.getOffer());

    private final Predicate<TradeInfo> isTaker = (t) -> t.getRole().toLowerCase().contains("taker");

    private final Function<TradeInfo, String> paymentCurrencyCode = (t) ->
            isFiatTrade.test(t)
                    ? t.getOffer().getCounterCurrencyCode()
                    : t.getOffer().getBaseCurrencyCode();

    private final Function<TradeInfo, Column<Long>> toPriceColumn = (t) -> {
        String colHeader = isFiatTrade.test(t)
                ? String.format(COL_HEADER_PRICE, t.getOffer().getCounterCurrencyCode())
                : String.format(COL_HEADER_PRICE_OF_ALTCOIN, t.getOffer().getBaseCurrencyCode());
        return isFiatTrade.test(t)
                ? new FiatColumn(colHeader)
                : new AltcoinColumn(colHeader);
    };

    private final Function<TradeInfo, Column<Long>> toAmountColumn = (t) -> {
        String headerCurrencyCode = t.getOffer().getBaseCurrencyCode();
        String colHeader = String.format(COL_HEADER_TRADE_AMOUNT, headerCurrencyCode);
        return isFiatTrade.test(t)
                ? new SatoshiColumn(colHeader)
                : new AltcoinColumn(colHeader, ALTCOIN_OFFER_VOLUME);
    };

    private final Function<TradeInfo, Long> toAmount = (t) ->
            isFiatTrade.test(t)
                    ? t.getTradeAmountAsLong()
                    : t.getTradeVolume();

    private final Function<TradeInfo, Long> toMinerTxFee = (t) ->
            isTaker.test(t)
                    ? t.getTxFeeAsLong()
                    : t.getOffer().getTxFee();

    private final Function<TradeInfo, Column<Long>> toBisqTradeFeeColumn = (t) -> {
        String headerCurrencyCode = isTaker.test(t)
                ? t.getIsCurrencyForTakerFeeBtc() ? "BTC" : "BSQ"
                : t.getOffer().getIsCurrencyForMakerFeeBtc() ? "BTC" : "BSQ";
        String colHeader = isTaker.test(t)
                ? String.format(COL_HEADER_TRADE_TAKER_FEE, headerCurrencyCode)
                : String.format(COL_HEADER_TRADE_MAKER_FEE, headerCurrencyCode);
        boolean isBsqSatoshis = headerCurrencyCode.equals("BSQ");
        return new SatoshiColumn(colHeader, isBsqSatoshis);
    };

    private final Function<TradeInfo, Long> toMakerTakerFee = (t) ->
            isTaker.test(t)
                    ? t.getTakerFeeAsLong()
                    : t.getOffer().getMakerFee();

    private final Function<TradeInfo, Column<Long>> toTradeCostColumn = (t) -> {
        String headerCurrencyCode = t.getOffer().getCounterCurrencyCode();
        String colHeader = String.format(COL_HEADER_TRADE_BUYER_COST, headerCurrencyCode);
        return isFiatTrade.test(t)
                ? new FiatColumn(colHeader, VOLUME)
                : new SatoshiColumn(colHeader);
    };

    private final Function<TradeInfo, Long> toTradeCost = (t) ->
            isFiatTrade.test(t)
                    ? t.getTradeVolume()
                    : t.getTradeAmountAsLong();

    private final Function<TradeInfo, Column<Boolean>> toPaymentSentColumn = (t) -> {
        String headerCurrencyCode = paymentCurrencyCode.apply(t);
        String colHeader = String.format(COL_HEADER_TRADE_PAYMENT_SENT, headerCurrencyCode);
        return new BooleanColumn(colHeader);
    };

    private final Function<TradeInfo, Column<Boolean>> toPaymentReceivedColumn = (t) -> {
        String headerCurrencyCode = paymentCurrencyCode.apply(t);
        String colHeader = String.format(COL_HEADER_TRADE_PAYMENT_RECEIVED, headerCurrencyCode);
        return new BooleanColumn(colHeader);
    };

    private final Predicate<TradeInfo> showAltCoinBuyerAddress = (t) -> {
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
    private final Function<TradeInfo, Column<String>> toAltcoinReceiveAddressColumn = (t) -> {
        if (showAltCoinBuyerAddress.test(t)) {
            String headerCurrencyCode = paymentCurrencyCode.apply(t);
            String colHeader = String.format(COL_HEADER_TRADE_ALTCOIN_BUYER_ADDRESS, headerCurrencyCode);
            return new StringColumn(colHeader);
        } else {
            return null;
        }
    };

    private final Function<TradeInfo, String> toAltcoinReceiveAddress = (t) -> {
        if (showAltCoinBuyerAddress.test(t)) {
            ContractInfo contract = t.getContract();
            boolean isBuyerMakerAndSellerTaker = contract.getIsBuyerMakerAndSellerTaker();
            return isBuyerMakerAndSellerTaker  // (is BTC buyer / maker)
                    ? contract.getTakerPaymentAccountPayload().getAddress()
                    : contract.getMakerPaymentAccountPayload().getAddress();
        } else {
            return "";
        }
    };
}
