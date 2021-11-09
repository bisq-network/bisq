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

import bisq.proto.grpc.BsqBalanceInfo;

import java.util.List;

import static bisq.cli.table.builder.TableBuilderConstants.*;
import static bisq.cli.table.builder.TableType.BSQ_BALANCE_TBL;



import bisq.cli.table.Table;
import bisq.cli.table.column.Column;
import bisq.cli.table.column.SatoshiColumn;

/**
 * Builds a {@code bisq.cli.table.Table} from a
 * {@code bisq.proto.grpc.BsqBalanceInfo} object.
 */
class BsqBalanceTableBuilder extends AbstractTableBuilder {

    // Default columns not dynamically generated with bsq balance info.
    private final Column<Long> colAvailableConfirmedBalance;
    private final Column<Long> colUnverifiedBalance;
    private final Column<Long> colUnconfirmedChangeBalance;
    private final Column<Long> colLockedForVotingBalance;
    private final Column<Long> colLockupBondsBalance;
    private final Column<Long> colUnlockingBondsBalance;

    BsqBalanceTableBuilder(List<?> protos) {
        super(BSQ_BALANCE_TBL, protos);
        this.colAvailableConfirmedBalance = new SatoshiColumn(COL_HEADER_AVAILABLE_CONFIRMED_BALANCE, true);
        this.colUnverifiedBalance = new SatoshiColumn(COL_HEADER_UNVERIFIED_BALANCE, true);
        this.colUnconfirmedChangeBalance = new SatoshiColumn(COL_HEADER_UNCONFIRMED_CHANGE_BALANCE, true);
        this.colLockedForVotingBalance = new SatoshiColumn(COL_HEADER_LOCKED_FOR_VOTING_BALANCE, true);
        this.colLockupBondsBalance = new SatoshiColumn(COL_HEADER_LOCKUP_BONDS_BALANCE, true);
        this.colUnlockingBondsBalance = new SatoshiColumn(COL_HEADER_UNLOCKING_BONDS_BALANCE, true);
    }

    public Table build() {
        BsqBalanceInfo balance = (BsqBalanceInfo) protos.get(0);

        // Populate columns with bsq balance info.

        colAvailableConfirmedBalance.addRow(balance.getAvailableConfirmedBalance());
        colUnverifiedBalance.addRow(balance.getUnverifiedBalance());
        colUnconfirmedChangeBalance.addRow(balance.getUnconfirmedChangeBalance());
        colLockedForVotingBalance.addRow(balance.getLockedForVotingBalance());
        colLockupBondsBalance.addRow(balance.getLockupBondsBalance());
        colUnlockingBondsBalance.addRow(balance.getUnlockingBondsBalance());

        // Define and return the table instance with populated columns.

        return new Table(colAvailableConfirmedBalance.asStringColumn(),
                colUnverifiedBalance.asStringColumn(),
                colUnconfirmedChangeBalance.asStringColumn(),
                colLockedForVotingBalance.asStringColumn(),
                colLockupBondsBalance.asStringColumn(),
                colUnlockingBondsBalance.asStringColumn());
    }
}
