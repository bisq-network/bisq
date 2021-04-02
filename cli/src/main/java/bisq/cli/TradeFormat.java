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

import bisq.proto.grpc.ContractInfo;
import bisq.proto.grpc.TradeInfo;

import com.google.common.annotations.VisibleForTesting;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static bisq.cli.ColumnHeaderConstants.*;
import static bisq.cli.CurrencyFormat.*;
import static com.google.common.base.Strings.padEnd;

@VisibleForTesting
public class TradeFormat {

    private static final String YES = "YES";
    private static final String NO = "NO";

    // TODO add String format(List<TradeInfo> trades)

    @VisibleForTesting
    public static String format(TradeInfo tradeInfo) {
        // Some column values might be longer than header, so we need to calculate them.
        int shortIdColWidth = Math.max(COL_HEADER_TRADE_SHORT_ID.length(), tradeInfo.getShortId().length());
        int roleColWidth = Math.max(COL_HEADER_TRADE_ROLE.length(), tradeInfo.getRole().length());

        // We only show taker fee under its header when user is the taker.
        boolean isTaker = tradeInfo.getRole().toLowerCase().contains("taker");
        Supplier<String> makerFeeHeader = () -> !isTaker ?
                COL_HEADER_TRADE_MAKER_FEE + COL_HEADER_DELIMITER
                : "";
        Supplier<String> makerFeeHeaderSpec = () -> !isTaker ?
                "%" + (COL_HEADER_TRADE_MAKER_FEE.length() + 2) + "s"
                : "";
        Supplier<String> takerFeeHeader = () -> isTaker ?
                COL_HEADER_TRADE_TAKER_FEE + COL_HEADER_DELIMITER
                : "";
        Supplier<String> takerFeeHeaderSpec = () -> isTaker ?
                "%" + (COL_HEADER_TRADE_TAKER_FEE.length() + 2) + "s"
                : "";

        boolean showBsqBuyerAddress = shouldShowBsqBuyerAddress(tradeInfo, isTaker);
        Supplier<String> bsqBuyerAddressHeader = () -> showBsqBuyerAddress ? COL_HEADER_TRADE_BSQ_BUYER_ADDRESS : "";
        Supplier<String> bsqBuyerAddressHeaderSpec = () -> showBsqBuyerAddress ? "%s" : "";

        String headersFormat = padEnd(COL_HEADER_TRADE_SHORT_ID, shortIdColWidth, ' ') + COL_HEADER_DELIMITER
                + padEnd(COL_HEADER_TRADE_ROLE, roleColWidth, ' ') + COL_HEADER_DELIMITER
                + priceHeader.apply(tradeInfo) + COL_HEADER_DELIMITER   // includes %s -> currencyCode
                + padEnd(COL_HEADER_TRADE_AMOUNT, 12, ' ') + COL_HEADER_DELIMITER
                + padEnd(COL_HEADER_TRADE_TX_FEE, 12, ' ') + COL_HEADER_DELIMITER
                + makerFeeHeader.get()
                //  maker or taker fee header, not both
                + takerFeeHeader.get()
                + COL_HEADER_TRADE_DEPOSIT_PUBLISHED + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_DEPOSIT_CONFIRMED + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_BUYER_COST + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_PAYMENT_SENT + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_PAYMENT_RECEIVED + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_PAYOUT_PUBLISHED + COL_HEADER_DELIMITER
                + COL_HEADER_TRADE_WITHDRAWN + COL_HEADER_DELIMITER
                + bsqBuyerAddressHeader.get()
                + "%n";

        String counterCurrencyCode = tradeInfo.getOffer().getCounterCurrencyCode();
        String baseCurrencyCode = tradeInfo.getOffer().getBaseCurrencyCode();

        String headerLine = String.format(headersFormat,
                /* COL_HEADER_PRICE */ priceHeaderCurrencyCode.apply(tradeInfo),
                /* COL_HEADER_TRADE_AMOUNT */ baseCurrencyCode,
                /* COL_HEADER_TRADE_(M||T)AKER_FEE */ makerTakerFeeHeaderCurrencyCode.apply(tradeInfo, isTaker),
                /* COL_HEADER_TRADE_BUYER_COST */ counterCurrencyCode,
                /* COL_HEADER_TRADE_PAYMENT_SENT */ paymentStatusHeaderCurrencyCode.apply(tradeInfo),
                /* COL_HEADER_TRADE_PAYMENT_RECEIVED */  paymentStatusHeaderCurrencyCode.apply(tradeInfo));

        String colDataFormat = "%-" + shortIdColWidth + "s"                 // lt justify
                + "  %-" + (roleColWidth + COL_HEADER_DELIMITER.length()) + "s" // left
                + "%" + (COL_HEADER_PRICE.length() - 1) + "s"               // rt justify
                + "%" + (COL_HEADER_TRADE_AMOUNT.length() + 1) + "s"        // rt justify
                + "%" + (COL_HEADER_TRADE_TX_FEE.length() + 1) + "s"        // rt justify
                + makerFeeHeaderSpec.get()                                      // rt justify
                //              OR (one of them is an empty string)
                + takerFeeHeaderSpec.get()                                      // rt justify
                + "  %-" + COL_HEADER_TRADE_DEPOSIT_PUBLISHED.length() + "s" // lt justify
                + "  %-" + COL_HEADER_TRADE_DEPOSIT_CONFIRMED.length() + "s" // lt justify
                + "%" + (COL_HEADER_TRADE_BUYER_COST.length() + 1) + "s"    // rt justify
                + "  %-" + (COL_HEADER_TRADE_PAYMENT_SENT.length() - 1) + "s" // left
                + "  %-" + (COL_HEADER_TRADE_PAYMENT_RECEIVED.length() - 1) + "s" // left
                + "  %-" + COL_HEADER_TRADE_PAYOUT_PUBLISHED.length() + "s" // lt justify
                + "  %-" + (COL_HEADER_TRADE_WITHDRAWN.length() + 2) + "s"
                + bsqBuyerAddressHeaderSpec.get();

        return headerLine + formatTradeData(colDataFormat, tradeInfo, isTaker, showBsqBuyerAddress);
    }

    private static String formatTradeData(String format,
                                          TradeInfo tradeInfo,
                                          boolean isTaker,
                                          boolean showBsqBuyerAddress) {
        return String.format(format,
                tradeInfo.getShortId(),
                tradeInfo.getRole(),
                priceFormat.apply(tradeInfo),
                amountFormat.apply(tradeInfo),
                makerTakerMinerTxFeeFormat.apply(tradeInfo, isTaker),
                makerTakerFeeFormat.apply(tradeInfo, isTaker),
                tradeInfo.getIsDepositPublished() ? YES : NO,
                tradeInfo.getIsDepositConfirmed() ? YES : NO,
                tradeCostFormat.apply(tradeInfo),
                tradeInfo.getIsFiatSent() ? YES : NO,
                tradeInfo.getIsFiatReceived() ? YES : NO,
                tradeInfo.getIsPayoutPublished() ? YES : NO,
                tradeInfo.getIsWithdrawn() ? YES : NO,
                bsqReceiveAddress.apply(tradeInfo, showBsqBuyerAddress));
    }

    private static final Function<TradeInfo, String> priceHeader = (t) ->
            t.getOffer().getBaseCurrencyCode().equals("BTC")
                    ? COL_HEADER_PRICE
                    : COL_HEADER_PRICE_OF_ALTCOIN;

    private static final Function<TradeInfo, String> priceHeaderCurrencyCode = (t) ->
            t.getOffer().getBaseCurrencyCode().equals("BTC")
                    ? t.getOffer().getCounterCurrencyCode()
                    : t.getOffer().getBaseCurrencyCode();

    private static final BiFunction<TradeInfo, Boolean, String> makerTakerFeeHeaderCurrencyCode = (t, isTaker) -> {
        if (isTaker) {
            return t.getIsCurrencyForTakerFeeBtc() ? "BTC" : "BSQ";
        } else {
            return t.getOffer().getIsCurrencyForMakerFeeBtc() ? "BTC" : "BSQ";
        }
    };

    private static final Function<TradeInfo, String> paymentStatusHeaderCurrencyCode = (t) ->
            t.getOffer().getBaseCurrencyCode().equals("BTC")
                    ? t.getOffer().getCounterCurrencyCode()
                    : t.getOffer().getBaseCurrencyCode();

    private static final Function<TradeInfo, String> priceFormat = (t) ->
            t.getOffer().getBaseCurrencyCode().equals("BTC")
                    ? formatPrice(t.getTradePrice())
                    : formatCryptoCurrencyPrice(t.getOffer().getPrice());

    private static final Function<TradeInfo, String> amountFormat = (t) ->
            t.getOffer().getBaseCurrencyCode().equals("BTC")
                    ? formatSatoshis(t.getTradeAmountAsLong())
                    : formatCryptoCurrencyOfferVolume(t.getOffer().getVolume());

    private static final BiFunction<TradeInfo, Boolean, String> makerTakerMinerTxFeeFormat = (t, isTaker) -> {
        if (isTaker) {
            return formatSatoshis(t.getTxFeeAsLong());
        } else {
            return formatSatoshis(t.getOffer().getTxFee());
        }
    };

    private static final BiFunction<TradeInfo, Boolean, String> makerTakerFeeFormat = (t, isTaker) -> {
        if (isTaker) {
            return t.getIsCurrencyForTakerFeeBtc()
                    ? formatSatoshis(t.getTakerFeeAsLong())
                    : formatBsq(t.getTakerFeeAsLong());
        } else {
            return t.getOffer().getIsCurrencyForMakerFeeBtc()
                    ? formatSatoshis(t.getOffer().getMakerFee())
                    : formatBsq(t.getOffer().getMakerFee());
        }
    };

    private static final Function<TradeInfo, String> tradeCostFormat = (t) ->
            t.getOffer().getBaseCurrencyCode().equals("BTC")
                    ? formatOfferVolume(t.getOffer().getVolume())
                    : formatSatoshis(t.getTradeAmountAsLong());

    private static final BiFunction<TradeInfo, Boolean, String> bsqReceiveAddress = (t, showBsqBuyerAddress) -> {
        if (showBsqBuyerAddress) {
            ContractInfo contract = t.getContract();
            boolean isBuyerMakerAndSellerTaker = contract.getIsBuyerMakerAndSellerTaker();
            return isBuyerMakerAndSellerTaker  // (is BTC buyer / maker)
                    ? contract.getTakerPaymentAccountPayload().getAddress()
                    : contract.getMakerPaymentAccountPayload().getAddress();
        } else {
            return "";
        }
    };

    private static boolean shouldShowBsqBuyerAddress(TradeInfo tradeInfo, boolean isTaker) {
        if (tradeInfo.getOffer().getBaseCurrencyCode().equals("BTC")) {
            return false;
        } else {
            ContractInfo contract = tradeInfo.getContract();
            // Do not forget buyer and seller refer to BTC buyer and seller, not BSQ
            // buyer and seller.  If you are buying BSQ, you are the (BTC) seller.
            boolean isBuyerMakerAndSellerTaker = contract.getIsBuyerMakerAndSellerTaker();
            if (isTaker) {
                return !isBuyerMakerAndSellerTaker;
            } else {
                return isBuyerMakerAndSellerTaker;
            }
        }
    }
}
