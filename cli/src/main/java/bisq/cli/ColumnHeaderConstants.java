package bisq.cli;

import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;

class ColumnHeaderConstants {

    // For inserting 2 spaces between column headers.
    static final String COL_HEADER_DELIMITER = "  ";

    // Table column header format specs, right padded with two spaces.  In some cases
    // such as COL_HEADER_CREATION_DATE, COL_HEADER_VOLUME and COL_HEADER_UUID, the
    // expected max data string length is accounted for.  In others, the column header length
    // are expected to be greater than any column value length.
    static final String COL_HEADER_ADDRESS = padEnd("Address", 34, ' ');
    static final String COL_HEADER_AMOUNT = padEnd("BTC(min - max)", 24, ' ');
    static final String COL_HEADER_BALANCE = padStart("Balance", 12, ' ');
    static final String COL_HEADER_CONFIRMATIONS = "Confirmations";
    static final String COL_HEADER_CREATION_DATE = padEnd("Creation Date (UTC)", 20, ' ');
    static final String COL_HEADER_CURRENCY = "Currency";
    static final String COL_HEADER_DIRECTION = "Buy/Sell";
    static final String COL_HEADER_NAME = "Name";
    static final String COL_HEADER_PAYMENT_METHOD = "Payment Method";
    static final String COL_HEADER_PRICE = "Price in %-3s for 1 BTC";
    static final String COL_HEADER_TRADE_AMOUNT = padStart("Amount(%-3s)", 12, ' ');
    static final String COL_HEADER_TRADE_DEPOSIT_CONFIRMED = "Deposit Confirmed";
    static final String COL_HEADER_TRADE_DEPOSIT_PUBLISHED = "Deposit Published";
    static final String COL_HEADER_TRADE_FIAT_SENT = "Fiat Sent";
    static final String COL_HEADER_TRADE_FIAT_RECEIVED = "Fiat Received";
    static final String COL_HEADER_TRADE_PAYOUT_PUBLISHED = "Payout Published";
    static final String COL_HEADER_TRADE_WITHDRAWN = "Withdrawn";
    static final String COL_HEADER_TRADE_ROLE = "My Role";
    static final String COL_HEADER_TRADE_SHORT_ID = "ID";
    static final String COL_HEADER_TRADE_TX_FEE = "Tx Fee(%-3s)";
    static final String COL_HEADER_TRADE_TAKER_FEE = "Taker Fee(%-3s)";
    static final String COL_HEADER_VOLUME = padEnd("%-3s(min - max)", 15, ' ');
    static final String COL_HEADER_UUID = padEnd("ID", 52, ' ');
}
