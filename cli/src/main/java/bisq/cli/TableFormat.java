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
import bisq.proto.grpc.BalancesInfo;
import bisq.proto.grpc.BsqBalanceInfo;
import bisq.proto.grpc.BtcBalanceInfo;

import protobuf.PaymentAccount;

import com.google.common.annotations.VisibleForTesting;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static bisq.cli.ColumnHeaderConstants.*;
import static bisq.cli.CurrencyFormat.formatBsq;
import static bisq.cli.CurrencyFormat.formatSatoshis;
import static com.google.common.base.Strings.padEnd;
import static java.lang.String.format;
import static java.util.Collections.max;
import static java.util.Comparator.comparing;
import static java.util.TimeZone.getTimeZone;

@Deprecated
@VisibleForTesting
public class TableFormat {

    static final TimeZone TZ_UTC = getTimeZone("UTC");
    static final SimpleDateFormat DATE_FORMAT_ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public static String formatAddressBalanceTbl(List<AddressBalanceInfo> addressBalanceInfo) {
        String headerFormatString = COL_HEADER_ADDRESS + COL_HEADER_DELIMITER
                + COL_HEADER_AVAILABLE_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_CONFIRMATIONS + COL_HEADER_DELIMITER
                + COL_HEADER_IS_USED_ADDRESS + COL_HEADER_DELIMITER + "\n";
        String headerLine = format(headerFormatString, "BTC");

        String colDataFormat = "%-" + COL_HEADER_ADDRESS.length() + "s" // lt justify
                + "  %" + (COL_HEADER_AVAILABLE_BALANCE.length() - 1) + "s" // rt justify
                + "  %" + COL_HEADER_CONFIRMATIONS.length() + "d"       // rt justify
                + "  %-" + COL_HEADER_IS_USED_ADDRESS.length() + "s";  // lt justify
        return headerLine
                + addressBalanceInfo.stream()
                .map(info -> format(colDataFormat,
                        info.getAddress(),
                        formatSatoshis(info.getBalance()),
                        info.getNumConfirmations(),
                        info.getIsAddressUnused() ? "NO" : "YES"))
                .collect(Collectors.joining("\n"));
    }

    public static String formatBalancesTbls(BalancesInfo balancesInfo) {
        return "BTC" + "\n"
                + formatBtcBalanceInfoTbl(balancesInfo.getBtc()) + "\n"
                + "BSQ" + "\n"
                + formatBsqBalanceInfoTbl(balancesInfo.getBsq());
    }

    public static String formatBsqBalanceInfoTbl(BsqBalanceInfo bsqBalanceInfo) {
        String headerLine = COL_HEADER_AVAILABLE_CONFIRMED_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_UNVERIFIED_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_UNCONFIRMED_CHANGE_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_LOCKED_FOR_VOTING_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_LOCKUP_BONDS_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_UNLOCKING_BONDS_BALANCE + COL_HEADER_DELIMITER + "\n";
        String colDataFormat = "%" + COL_HEADER_AVAILABLE_CONFIRMED_BALANCE.length() + "s" // rt justify
                + " %" + (COL_HEADER_UNVERIFIED_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_UNCONFIRMED_CHANGE_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_LOCKED_FOR_VOTING_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_LOCKUP_BONDS_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_UNLOCKING_BONDS_BALANCE.length() + 1) + "s"; // rt justify
        return headerLine + format(colDataFormat,
                formatBsq(bsqBalanceInfo.getAvailableConfirmedBalance()),
                formatBsq(bsqBalanceInfo.getUnverifiedBalance()),
                formatBsq(bsqBalanceInfo.getUnconfirmedChangeBalance()),
                formatBsq(bsqBalanceInfo.getLockedForVotingBalance()),
                formatBsq(bsqBalanceInfo.getLockupBondsBalance()),
                formatBsq(bsqBalanceInfo.getUnlockingBondsBalance()));
    }

    public static String formatBtcBalanceInfoTbl(BtcBalanceInfo btcBalanceInfo) {
        String headerLine = COL_HEADER_AVAILABLE_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_RESERVED_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_TOTAL_AVAILABLE_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_LOCKED_BALANCE + COL_HEADER_DELIMITER + "\n";
        String colDataFormat = "%" + COL_HEADER_AVAILABLE_BALANCE.length() + "s" // rt justify
                + " %" + (COL_HEADER_RESERVED_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_TOTAL_AVAILABLE_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_LOCKED_BALANCE.length() + 1) + "s"; // rt justify
        return headerLine + format(colDataFormat,
                formatSatoshis(btcBalanceInfo.getAvailableBalance()),
                formatSatoshis(btcBalanceInfo.getReservedBalance()),
                formatSatoshis(btcBalanceInfo.getTotalAvailableBalance()),
                formatSatoshis(btcBalanceInfo.getLockedBalance()));
    }

    public static String formatPaymentAcctTbl(List<PaymentAccount> paymentAccounts) {
        // Some column values might be longer than header, so we need to calculate them.
        int nameColWidth = getLongestColumnSize(
                COL_HEADER_NAME.length(),
                paymentAccounts.stream().map(PaymentAccount::getAccountName)
                        .collect(Collectors.toList()));
        int paymentMethodColWidth = getLongestColumnSize(
                COL_HEADER_PAYMENT_METHOD.length(),
                paymentAccounts.stream().map(a -> a.getPaymentMethod().getId())
                        .collect(Collectors.toList()));
        String headerLine = padEnd(COL_HEADER_NAME, nameColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_CURRENCY + COL_HEADER_DELIMITER
                + padEnd(COL_HEADER_PAYMENT_METHOD, paymentMethodColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_UUID + COL_HEADER_DELIMITER + "\n";
        String colDataFormat = "%-" + nameColWidth + "s"            // left justify
                + "  %-" + COL_HEADER_CURRENCY.length() + "s"       // left justify
                + "  %-" + paymentMethodColWidth + "s"              // left justify
                + "  %-" + COL_HEADER_UUID.length() + "s";          // left justify
        return headerLine
                + paymentAccounts.stream()
                .map(a -> format(colDataFormat,
                        a.getAccountName(),
                        a.getSelectedTradeCurrency().getCode(),
                        a.getPaymentMethod().getId(),
                        a.getId()))
                .collect(Collectors.joining("\n"));
    }

    // Return size of the longest string value, or the header.len, whichever is greater.
    static int getLongestColumnSize(int headerLength, List<String> strings) {
        int longest = max(strings, comparing(String::length)).length();
        return Math.max(longest, headerLength);
    }

    static String formatTimestamp(long timestamp) {
        DATE_FORMAT_ISO_8601.setTimeZone(TZ_UTC);
        return DATE_FORMAT_ISO_8601.format(new Date(timestamp));
    }
}
