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

import bisq.proto.grpc.BtcBalanceInfo;

import java.util.List;

import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_AVAILABLE_BALANCE;
import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_LOCKED_BALANCE;
import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_RESERVED_BALANCE;
import static bisq.cli.table.builder.TableBuilderConstants.COL_HEADER_TOTAL_AVAILABLE_BALANCE;
import static bisq.cli.table.builder.TableType.BTC_BALANCE_TBL;



import bisq.cli.table.Table;
import bisq.cli.table.column.Column;
import bisq.cli.table.column.SatoshiColumn;

/**
 * Builds a {@code bisq.cli.table.Table} from a
 * {@code bisq.proto.grpc.BtcBalanceInfo} object.
 */
class BtcBalanceTableBuilder extends AbstractTableBuilder {

    // Default columns not dynamically generated with btc balance info.
    private final Column<Long> colAvailableBalance;
    private final Column<Long> colReservedBalance;
    private final Column<Long> colTotalAvailableBalance;
    private final Column<Long> colLockedBalance;

    BtcBalanceTableBuilder(List<?> protos) {
        super(BTC_BALANCE_TBL, protos);
        this.colAvailableBalance = new SatoshiColumn(COL_HEADER_AVAILABLE_BALANCE);
        this.colReservedBalance = new SatoshiColumn(COL_HEADER_RESERVED_BALANCE);
        this.colTotalAvailableBalance = new SatoshiColumn(COL_HEADER_TOTAL_AVAILABLE_BALANCE);
        this.colLockedBalance = new SatoshiColumn(COL_HEADER_LOCKED_BALANCE);
    }

    public Table build() {
        BtcBalanceInfo balance = (BtcBalanceInfo) protos.get(0);

        // Populate columns with btc balance info.

        colAvailableBalance.addRow(balance.getAvailableBalance());
        colReservedBalance.addRow(balance.getReservedBalance());
        colTotalAvailableBalance.addRow(balance.getTotalAvailableBalance());
        colLockedBalance.addRow(balance.getLockedBalance());

        // Define and return the table instance with populated columns.

        return new Table(colAvailableBalance.asStringColumn(),
                colReservedBalance.asStringColumn(),
                colTotalAvailableBalance.asStringColumn(),
                colLockedBalance.asStringColumn());
    }
}
