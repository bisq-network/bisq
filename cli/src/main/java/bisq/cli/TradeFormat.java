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

package bisq.cli;

import bisq.proto.grpc.TradeInfo;

import com.google.common.annotations.VisibleForTesting;

import java.util.function.Supplier;

import static bisq.cli.ColumnHeaderConstants.*;
import static bisq.cli.CurrencyFormat.formatOfferPrice;
import static bisq.cli.CurrencyFormat.formatOfferVolume;
import static bisq.cli.CurrencyFormat.formatSatoshis;
import static com.google.common.base.Strings.padEnd;

@VisibleForTesting
public class TradeFormat {

    @VisibleForTesting
    public static String format(TradeInfo tradeInfo) {
        // Some column values might be longer than header, so we need to calculate them.
        int shortIdColWidth = Math.max(COL_HEADER_TRADE_SHORT_ID.length(), tradeInfo.getShortId().length());
        int roleColWidth = Math.max(COL_HEADER_TRADE_ROLE.length(), tradeInfo.getRole().length());

        // We only show taker fee under its header when user is the taker.
        boolean isTaker = tradeInfo.getRole().toLowerCase().contains("taker");
        Supplier<String> takerFeeHeaderFormat = () -> isTaker ?
                padEnd(COL_HEADER_TRADE_TAKER_FEE, 12, ' ') + COL_HEADER_DELIMITER
                : "";
        Supplier<String> takerFeeHeader = () -> isTaker ?
                "%" + (COL_HEADER_TRADE_TAKER_FEE.length() + 1) + "s"
                : "";

        String headersFormat = padEnd(COL_HEADER_TRADE_SHORT_ID, shortIdColWidth, ' ') + COL_HEADER_DELIMITER
                + padEnd(COL_HEADER_TRADE_ROLE, roleColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_PRICE + COL_HEADER_DELIMITER   // includes %s -> currencyCode
                + padEnd(COL_HEADER_TRADE_AMOUNT, 12, ' ') + COL_HEADER_DELIMITER
                + padEnd(COL_HEADER_TRADE_TX_FEE, 12, ' ') + COL_HEADER_DELIMITER
                + takerFeeHeaderFormat.get()
                + COL_HEADER_TRADE_DEPOSIT_PUBLISHED + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_DEPOSIT_CONFIRMED + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_BUYER_COST + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_PAYMENT_SENT + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_PAYMENT_RECEIVED + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_PAYOUT_PUBLISHED + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_WITHDRAWN + COL_HEADER_DELIMITER
                + "%n";

        String counterCurrencyCode = tradeInfo.getOffer().getCounterCurrencyCode();
        String baseCurrencyCode = tradeInfo.getOffer().getBaseCurrencyCode();

        // The taker's output contains an extra taker tx fee column.
        String headerLine = isTaker
                ? String.format(headersFormat,
                /* COL_HEADER_PRICE */ counterCurrencyCode,
                /* COL_HEADER_TRADE_AMOUNT */ baseCurrencyCode,
                /* COL_HEADER_TRADE_TX_FEE */ baseCurrencyCode,
                /* COL_HEADER_TRADE_TAKER_FEE */ baseCurrencyCode,
                /* COL_HEADER_TRADE_BUYER_COST */ counterCurrencyCode,
                /* COL_HEADER_TRADE_PAYMENT_SENT */ counterCurrencyCode,
                /* COL_HEADER_TRADE_PAYMENT_RECEIVED */ counterCurrencyCode)
                : String.format(headersFormat,
                /* COL_HEADER_PRICE */ counterCurrencyCode,
                /* COL_HEADER_TRADE_AMOUNT */ baseCurrencyCode,
                /* COL_HEADER_TRADE_TX_FEE */ baseCurrencyCode,
                /* COL_HEADER_TRADE_BUYER_COST */ counterCurrencyCode,
                /* COL_HEADER_TRADE_PAYMENT_SENT */ counterCurrencyCode,
                /* COL_HEADER_TRADE_PAYMENT_RECEIVED */ counterCurrencyCode);

        String colDataFormat = "%-" + shortIdColWidth + "s"                 // lt justify
                + "  %-" + (roleColWidth + COL_HEADER_DELIMITER.length()) + "s" // left
                + "%" + (COL_HEADER_PRICE.length() - 1) + "s"               // rt justify
                + "%" + (COL_HEADER_TRADE_AMOUNT.length() + 1) + "s"        // rt justify
                + "%" + (COL_HEADER_TRADE_TX_FEE.length() + 1) + "s"        // rt justify
                + takerFeeHeader.get()                                      // rt justify
                + "  %-" + COL_HEADER_TRADE_DEPOSIT_PUBLISHED.length() + "s" // lt justify
                + "  %-" + COL_HEADER_TRADE_DEPOSIT_CONFIRMED.length() + "s" // lt justify
                + "%" + (COL_HEADER_TRADE_BUYER_COST.length() + 1) + "s"    // rt justify
                + "  %-" + (COL_HEADER_TRADE_PAYMENT_SENT.length() - 1) + "s" // left
                + "  %-" + (COL_HEADER_TRADE_PAYMENT_RECEIVED.length() - 1) + "s" // left
                + "  %-" + COL_HEADER_TRADE_PAYOUT_PUBLISHED.length() + "s" // lt justify
                + "  %-" + COL_HEADER_TRADE_WITHDRAWN.length() + "s";       // lt justify

        return headerLine +
                (isTaker
                        ? formatTradeForTaker(colDataFormat, tradeInfo)
                        : formatTradeForMaker(colDataFormat, tradeInfo));
    }

    private static String formatTradeForMaker(String format, TradeInfo tradeInfo) {
        return String.format(format,
                tradeInfo.getShortId(),
                tradeInfo.getRole(),
                formatOfferPrice(tradeInfo.getTradePrice()),
                formatSatoshis(tradeInfo.getTradeAmountAsLong()),
                formatSatoshis(tradeInfo.getTxFeeAsLong()),
                tradeInfo.getIsDepositPublished() ? "YES" : "NO",
                tradeInfo.getIsDepositConfirmed() ? "YES" : "NO",
                formatOfferVolume(tradeInfo.getOffer().getVolume()),
                tradeInfo.getIsFiatSent() ? "YES" : "NO",
                tradeInfo.getIsFiatReceived() ? "YES" : "NO",
                tradeInfo.getIsPayoutPublished() ? "YES" : "NO",
                tradeInfo.getIsWithdrawn() ? "YES" : "NO");
    }

    private static String formatTradeForTaker(String format, TradeInfo tradeInfo) {
        return String.format(format,
                tradeInfo.getShortId(),
                tradeInfo.getRole(),
                formatOfferPrice(tradeInfo.getTradePrice()),
                formatSatoshis(tradeInfo.getTradeAmountAsLong()),
                formatSatoshis(tradeInfo.getTxFeeAsLong()),
                formatSatoshis(tradeInfo.getTakerFeeAsLong()),
                tradeInfo.getIsDepositPublished() ? "YES" : "NO",
                tradeInfo.getIsDepositConfirmed() ? "YES" : "NO",
                formatOfferVolume(tradeInfo.getOffer().getVolume()),
                tradeInfo.getIsFiatSent() ? "YES" : "NO",
                tradeInfo.getIsFiatReceived() ? "YES" : "NO",
                tradeInfo.getIsPayoutPublished() ? "YES" : "NO",
                tradeInfo.getIsWithdrawn() ? "YES" : "NO");
    }
}
