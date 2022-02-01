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

import static java.util.Collections.singletonList;



import bisq.cli.table.Table;

/**
 * Table builder factory.  It is not conventionally named TableBuilderFactory because
 * it has no static factory methods.  The number of static fields and methods in the
 * {@code bisq.cli.table} are kept to a minimum in an effort o reduce class load time
 * in the session-less CLI.
 */
public class TableBuilder extends AbstractTableBuilder {

    public TableBuilder(TableType tableType, Object proto) {
        this(tableType, singletonList(proto));
    }

    public TableBuilder(TableType tableType, List<?> protos) {
        super(tableType, protos);
    }

    public Table build() {
        switch (tableType) {
            case ADDRESS_BALANCE_TBL:
                return new AddressBalanceTableBuilder(protos).build();
            case BSQ_BALANCE_TBL:
                return new BsqBalanceTableBuilder(protos).build();
            case BTC_BALANCE_TBL:
                return new BtcBalanceTableBuilder(protos).build();
            case CLOSED_TRADES_TBL:
                return new ClosedTradeTableBuilder(protos).build();
            case FAILED_TRADES_TBL:
                return new FailedTradeTableBuilder(protos).build();
            case OFFER_TBL:
                return new OfferTableBuilder(protos).build();
            case OPEN_TRADES_TBL:
                return new OpenTradeTableBuilder(protos).build();
            case PAYMENT_ACCOUNT_TBL:
                return new PaymentAccountTableBuilder(protos).build();
            case TRADE_DETAIL_TBL:
                return new TradeDetailTableBuilder(protos).build();
            case TRANSACTION_TBL:
                return new TransactionTableBuilder(protos).build();
            default:
                throw new IllegalArgumentException("invalid cli table type " + tableType.name());
        }
    }
}
