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

import bisq.proto.grpc.AddressBalanceInfo;
import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static bisq.cli.CurrencyFormat.formatAmountRange;
import static bisq.cli.CurrencyFormat.formatOfferPrice;
import static bisq.cli.CurrencyFormat.formatSatoshis;
import static bisq.cli.CurrencyFormat.formatVolumeRange;
import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;
import static java.lang.String.format;
import static java.util.Collections.max;
import static java.util.Comparator.comparing;
import static java.util.TimeZone.getTimeZone;

class TableFormat {

    private static final TimeZone TZ_UTC = getTimeZone("UTC");
    private static final SimpleDateFormat DATE_FORMAT_ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    // For inserting 2 spaces between column headers.
    private static final String COL_HEADER_DELIMITER = "  ";

    // Table column header format specs, right padded with two spaces.  In some cases
    // such as COL_HEADER_CREATION_DATE, COL_HEADER_VOLUME and COL_HEADER_UUID, the
    // expected max data string length is accounted for.  In others, the column header length
    // are expected to be greater than any column value length.
    private static final String COL_HEADER_ADDRESS = padEnd("Address", 34, ' ');
    private static final String COL_HEADER_AMOUNT = padEnd("BTC(min - max)", 24, ' ');
    private static final String COL_HEADER_BALANCE = padStart("Balance", 12, ' ');
    private static final String COL_HEADER_CONFIRMATIONS = "Confirmations";
    private static final String COL_HEADER_CREATION_DATE = padEnd("Creation Date (UTC)", 20, ' ');
    private static final String COL_HEADER_CURRENCY = "Currency";
    private static final String COL_HEADER_DIRECTION = "Buy/Sell";  // TODO "Take Offer to
    private static final String COL_HEADER_NAME = "Name";
    private static final String COL_HEADER_PAYMENT_METHOD = "Payment Method";
    private static final String COL_HEADER_PRICE = "Price in %-3s for 1 BTC";
    private static final String COL_HEADER_VOLUME = padEnd("%-3s(min - max)", 15, ' ');
    private static final String COL_HEADER_UUID = padEnd("ID", 52, ' ');

    static String formatAddressBalanceTbl(List<AddressBalanceInfo> addressBalanceInfo) {
        String headerLine = (COL_HEADER_ADDRESS + COL_HEADER_DELIMITER
                + COL_HEADER_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_CONFIRMATIONS + COL_HEADER_DELIMITER + "\n");
        String colDataFormat = "%-" + COL_HEADER_ADDRESS.length() + "s" // left justify
                + "  %" + COL_HEADER_BALANCE.length() + "s" // right justify
                + "  %" + COL_HEADER_CONFIRMATIONS.length() + "d"; // right justify
        return headerLine
                + addressBalanceInfo.stream()
                .map(info -> format(colDataFormat,
                        info.getAddress(),
                        formatSatoshis(info.getBalance()),
                        info.getNumConfirmations()))
                .collect(Collectors.joining("\n"));
    }

    static String formatOfferTable(List<OfferInfo> offerInfo, String fiatCurrency) {

        // Some column values might be longer than header, so we need to calculated them.
        int paymentMethodColWidth = getLengthOfLongestColumn(
                COL_HEADER_PAYMENT_METHOD.length(),
                offerInfo.stream()
                        .map(OfferInfo::getPaymentMethodShortName)
                        .collect(Collectors.toList()));

        String headersFormat = COL_HEADER_DIRECTION + COL_HEADER_DELIMITER
                + COL_HEADER_PRICE + COL_HEADER_DELIMITER   // includes %s -> fiatCurrency
                + COL_HEADER_AMOUNT + COL_HEADER_DELIMITER
                + COL_HEADER_VOLUME + COL_HEADER_DELIMITER  // includes %s -> fiatCurrency
                + padEnd(COL_HEADER_PAYMENT_METHOD, paymentMethodColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_CREATION_DATE + COL_HEADER_DELIMITER
                + COL_HEADER_UUID.trim() + "%n";
        String headerLine = format(headersFormat, fiatCurrency, fiatCurrency);

        String colDataFormat = "%-" + (COL_HEADER_DIRECTION.length() + COL_HEADER_DELIMITER.length()) + "s" // left
                + "%" + (COL_HEADER_PRICE.length() - 1) + "s" // rt justify to end of hdr
                + "  %-" + (COL_HEADER_AMOUNT.length() - 1) + "s" // left justify
                + "  %" + COL_HEADER_VOLUME.length() + "s" // right justify
                + "  %-" + paymentMethodColWidth + "s" // left justify
                + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s" // left justify
                + "  %-" + COL_HEADER_UUID.length() + "s";
        return headerLine
                + offerInfo.stream()
                .map(o -> format(colDataFormat,
                        o.getDirection(),
                        formatOfferPrice(o.getPrice()),
                        formatAmountRange(o.getMinAmount(), o.getAmount()),
                        formatVolumeRange(o.getMinVolume(), o.getVolume()),
                        o.getPaymentMethodShortName(),
                        formatTimestamp(o.getDate()),
                        o.getId()))
                .collect(Collectors.joining("\n"));
    }

    static String formatPaymentAcctTbl(List<PaymentAccount> paymentAccounts) {
        // Some column values might be longer than header, so we need to calculated them.
        int nameColWidth = getLengthOfLongestColumn(
                COL_HEADER_NAME.length(),
                paymentAccounts.stream().map(PaymentAccount::getAccountName)
                        .collect(Collectors.toList()));
        int paymentMethodColWidth = getLengthOfLongestColumn(
                COL_HEADER_PAYMENT_METHOD.length(),
                paymentAccounts.stream().map(a -> a.getPaymentMethod().getId())
                        .collect(Collectors.toList()));

        String headerLine = padEnd(COL_HEADER_NAME, nameColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_CURRENCY + COL_HEADER_DELIMITER
                + padEnd(COL_HEADER_PAYMENT_METHOD, paymentMethodColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_UUID + COL_HEADER_DELIMITER + "\n";
        String colDataFormat = "%-" + nameColWidth + "s"        // left justify
                + "  %" + COL_HEADER_CURRENCY.length() + "s"    // right justify
                + "  %-" + paymentMethodColWidth + "s"          // left justify
                + "  %-" + COL_HEADER_UUID.length() + "s";      // left justify
        return headerLine
                + paymentAccounts.stream()
                .map(a -> format(colDataFormat,
                        a.getAccountName(),
                        a.getSelectedTradeCurrency().getCode(),
                        a.getPaymentMethod().getId(),
                        a.getId()))
                .collect(Collectors.joining("\n"));
    }

    // Return length of the longest string value, or the header.len, whichever is greater.
    private static int getLengthOfLongestColumn(int headerLength, List<String> strings) {
        int longest = max(strings, comparing(String::length)).length();
        return Math.max(longest, headerLength);
    }

    private static String formatTimestamp(long timestamp) {
        DATE_FORMAT_ISO_8601.setTimeZone(TZ_UTC);
        return DATE_FORMAT_ISO_8601.format(new Date(timestamp));
    }
}
