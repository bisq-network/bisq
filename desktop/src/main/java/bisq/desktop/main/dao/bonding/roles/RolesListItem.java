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
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.locale.Res;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
class RolesListItem {
    private final DaoFacade daoFacade;
    private final BondedRole bondedRole;
    private final boolean lockupActionRow;
    @Nullable
    private String verificationTxId;
    private boolean verificationTxIdResolved;

    RolesListItem(BondedRole bondedRole, DaoFacade daoFacade, boolean lockupActionRow) {
        this.daoFacade = daoFacade;
        this.bondedRole = bondedRole;
        this.lockupActionRow = lockupActionRow;
    }

    // Signing and verification must use the same tx, so both go through the verification tx of the role.
    // Resolving it iterates the evaluated proposal list, so we cache it as it gets called at each table cell update.
    // The items get recreated at each list update, thus the cache cannot get stale.
    public String getVerificationTxId() {
        if (!verificationTxIdResolved) {
            verificationTxId = getLockupTxId() == null
                    ? null
                    : daoFacade.findBondedRoleVerificationTxId(getRole(), getLockupTxId()).orElse(null);
            verificationTxIdResolved = true;
        }
        return verificationTxId;
    }

    public Optional<String> getRegistrationSignatureMessage(String profileId) {
        return daoFacade.getBondedRoleRegistrationSignatureMessage(getRole(), getLockupTxId(), profileId);
    }

    public String getLockupTxId() {
        return this.bondedRole.getLockupTxId();
    }

    public Role getRole() {
        return this.bondedRole.getBondedAsset();
    }

    public String getName() {
        return this.getRole().getName();
    }

    public String getLink() {
        return this.getRole().getLink();
    }

    public BondedRoleType getType() {
        return this.getRole().getBondedRoleType();
    }

    public String getTypeAsString() {
        return this.getRole().getBondedRoleType().getDisplayString();
    }

    public long getLockupDate() {
        return this.bondedRole.getLockupDate();
    }

    public boolean iAmOwner() {
        return this.daoFacade.isMyRole(this.getRole());
    }

    public String getBondStateAsString() {
        return Res.get("dao.bond.bondState." + bondedRole.getBondState().name());
    }

    public boolean isLockupButtonVisible() {
        return iAmOwner() && lockupActionRow;
    }

    public boolean isRevokeButtonVisible() {
        return getLockupTxId() != null &&
                daoFacade.isMyBondedRoleLockupTx(getLockupTxId()) &&
                bondedRole.getBondState() == BondState.LOCKUP_TX_CONFIRMED;
    }

    public boolean isSignButtonVisible() {
        return iAmOwner() && bondedRole.getBondState() == BondState.LOCKUP_TX_CONFIRMED &&
                getVerificationTxId() != null;
    }

    public boolean isVerifyButtonVisible() {
        return bondedRole.getBondState() == BondState.LOCKUP_TX_CONFIRMED &&
                getVerificationTxId() != null;
    }
}
