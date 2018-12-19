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

package bisq.desktop.main.dao.bonding.roles;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.locale.Res;

import java.util.Date;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
class RolesListItem {
    private final BondedRole bondedRole;
    private final Role role;
    private final String buttonText;
    private final boolean isButtonVisible;
    private final BondState bondState;
    private final String bondStateString;
    private final String lockupTxId;
    private final Date lockupDate;

    RolesListItem(BondedRole bondedRole,
                  DaoFacade daoFacade) {
        this.bondedRole = bondedRole;

        role = bondedRole.getBondedAsset();
        boolean isMyRole = daoFacade.isMyRole(role);
        bondState = bondedRole.getBondState();
        lockupTxId = bondedRole.getLockupTxId();
        lockupDate = new Date(bondedRole.getLockupDate());
        bondStateString = Res.get("dao.bond.bondState." + bondedRole.getBondState().name());

        boolean showLockup = bondedRole.getBondState() == BondState.READY_FOR_LOCKUP;
        boolean showRevoke = bondedRole.getBondState() == BondState.LOCKUP_TX_CONFIRMED;
        if (showLockup) {
            buttonText = Res.get("dao.bond.table.button.lockup");
        } else if (showRevoke) {
            buttonText = Res.get("dao.bond.table.button.revoke");
        } else {
            buttonText = "";
        }

        isButtonVisible = isMyRole && (showLockup || showRevoke);
    }
}
