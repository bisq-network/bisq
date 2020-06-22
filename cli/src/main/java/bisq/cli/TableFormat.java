package bisq.cli;

import bisq.proto.grpc.AddressBalanceInfo;
import bisq.proto.grpc.OfferInfo;

import java.text.DateFormat;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static bisq.cli.CurrencyFormat.formatAmountRange;
import static bisq.cli.CurrencyFormat.formatOfferPrice;
import static bisq.cli.CurrencyFormat.formatSatoshis;
import static bisq.cli.CurrencyFormat.formatVolumeRange;
import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;
import static java.lang.String.format;
import static java.text.DateFormat.DEFAULT;
import static java.text.DateFormat.getDateInstance;
import static java.text.DateFormat.getTimeInstance;
import static java.util.Collections.max;
import static java.util.Comparator.comparing;
import static java.util.TimeZone.getTimeZone;

class TableFormat {

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
    private static final String COL_HEADER_CREATION_DATE = padEnd("Creation Date", 24, ' ');
    private static final String COL_HEADER_DIRECTION = "Buy/Sell";  // TODO "Take Offer to
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
                        .map(o -> o.getPaymentMethodShortName())
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
                        o.getDirection().equals("BUY") ? "SELL" : "BUY",
                        formatOfferPrice(o.getPrice()),
                        formatAmountRange(o.getMinAmount(), o.getAmount()),
                        formatVolumeRange(o.getMinVolume(), o.getVolume()),
                        o.getPaymentMethodShortName(),
                        formatDateTime(o.getDate(), true),
                        o.getId()))
                .collect(Collectors.joining("\n"));
    }

    static String formatPaymentAcctTbl() {
        /*
        case getpaymentaccts: {
                    var request = GetPaymentAccountsRequest.newBuilder().build();
                    var reply = paymentAccountsService.getPaymentAccounts(request);
                    var columnFormatSpec = "%-41s %-25s %-14s %s";
                    out.println(format(columnFormatSpec, "ID", "Name", "Currency", "Payment Method"));
                    out.println(reply.getPaymentAccountsList().stream()
                            .map(a -> format(columnFormatSpec,
                                    a.getId(),
                                    a.getAccountName(),
                                    a.getSelectedTradeCurrency().getCode(),
                                    a.getPaymentMethod().getId()))
                            .collect(Collectors.joining("\n")));
                    return;
                }
         */
        return "";
    }

    // Return length of the longest string value, or the header.len, whichever is greater.
    private static int getLengthOfLongestColumn(int headerLength, List<String> strings) {
        int longest = max(strings, comparing(s -> s.length())).length();
        return longest > headerLength ? longest : headerLength;
    }

    private static String formatDateTime(long timestamp, boolean useLocaleAndLocalTimezone) {
        Date date = new Date(timestamp);
        Locale locale = useLocaleAndLocalTimezone ? Locale.getDefault() : Locale.US;
        DateFormat dateInstance = getDateInstance(DEFAULT, locale);
        DateFormat timeInstance = getTimeInstance(DEFAULT, locale);
        if (!useLocaleAndLocalTimezone) {
            dateInstance.setTimeZone(getTimeZone("UTC"));
            timeInstance.setTimeZone(getTimeZone("UTC"));
        }
        return formatDateTime(date, dateInstance, timeInstance);
    }

    private static String formatDateTime(Date date, DateFormat dateFormatter, DateFormat timeFormatter) {
        if (date != null) {
            return dateFormatter.format(date) + " " + timeFormatter.format(date);
        } else {
            return "";
        }
    }
}
