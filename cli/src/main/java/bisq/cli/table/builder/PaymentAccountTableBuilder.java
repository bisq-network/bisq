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

import protobuf.PaymentAccount;

import java.util.List;
import java.util.stream.Collectors;

import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_CURRENCY;
import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_NAME;
import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_PAYMENT_METHOD;
import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_UUID;
import static bisq.cli.table.builder.TableType.PAYMENT_ACCOUNT_TBL;



import bisq.cli.table.Table;
import bisq.cli.table.column.Column;
import bisq.cli.table.column.StringColumn;

/**
 * Builds a {@code bisq.cli.table.Table} from a List of
 * {@code protobuf.PaymentAccount} objects.
 */
class PaymentAccountTableBuilder extends AbstractTableBuilder {

    // Default columns not dynamically generated with payment account info.
    private final Column<String> colName;
    private final Column<String> colCurrency;
    private final Column<String> colPaymentMethod;
    private final Column<String> colId;

    PaymentAccountTableBuilder(List<?> protos) {
        super(PAYMENT_ACCOUNT_TBL, protos);
        this.colName = new StringColumn(COL_HEADER_NAME);
        this.colCurrency = new StringColumn(COL_HEADER_CURRENCY);
        this.colPaymentMethod = new StringColumn(COL_HEADER_PAYMENT_METHOD);
        this.colId = new StringColumn(COL_HEADER_UUID);
    }

    public Table build() {
        List<PaymentAccount> paymentAccounts = protos.stream()
                .map(a -> (PaymentAccount) a)
                .collect(Collectors.toList());

        // Populate columns with payment account info.
        //noinspection SimplifyStreamApiCallChains
        paymentAccounts.stream().forEachOrdered(a -> {
            colName.addRow(a.getAccountName());
            colCurrency.addRow(a.getSelectedTradeCurrency().getCode());
            colPaymentMethod.addRow(a.getPaymentMethod().getId());
            colId.addRow(a.getId());
        });

        // Define and return the table instance with populated columns.
        return new Table(colName, colCurrency, colPaymentMethod, colId);
    }
}
