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

import bisq.desktop.components.AutoTooltipButton;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.governance.role.BondedRoleState;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import javafx.scene.control.Label;

import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode
@Data
class BondedRolesListItem implements DaoStateListener {
    private final BondedRoleState bondedRoleState;
    private final DaoFacade daoFacade;
    private final BsqFormatter bsqFormatter;
    private final AutoTooltipButton button;
    private final Label label;
    private final BondedRole bondedRole;

    BondedRolesListItem(BondedRoleState bondedRoleState,
                        DaoFacade daoFacade,
                        BsqFormatter bsqFormatter) {
        this.bondedRoleState = bondedRoleState;
        this.daoFacade = daoFacade;
        this.bsqFormatter = bsqFormatter;

        bondedRole = bondedRoleState.getBondedRole();

        daoFacade.addBsqStateListener(this);

        button = new AutoTooltipButton();
        button.setMinWidth(70);

        label = new Label();

        update();
    }

    public String getStartDate() {
        return bondedRoleState.getStartDate() > 0 ?
                bsqFormatter.formatDateTime(new Date(bondedRoleState.getStartDate())) :
                "-";
    }

    public String getRevokeDate() {
        return bondedRoleState.getRevokeDate() > 0 ?
                bsqFormatter.formatDateTime(new Date(bondedRoleState.getRevokeDate())) :
                "-";
    }

    public void cleanup() {
        daoFacade.removeBsqStateListener(this);
        setOnAction(null);
    }

    public void setOnAction(Runnable handler) {
        button.setOnAction(e -> handler.run());
    }

    public boolean isBonded() {
        return bondedRoleState.isLockedUp();
    }

    private void update() {
        // We have following state:
        // 1. Not bonded: !isLockedUp, !isUnlocked, !isUnlocking: notBonded
        // 2. Locked up:   isLockedUp, !isUnlocked, !isUnlocking: lockedUp
        // 3. Unlocking:   isLockedUp,  isUnlocked,  isUnlocking: unlocking
        // 4. Unlocked:    isLockedUp,  isUnlocked, !isUnlocking: unlocked

        boolean isLockedUp = bondedRoleState.isLockedUp();
        boolean isUnlocked = bondedRoleState.isUnlocked();
        boolean isUnlocking = bondedRoleState.isUnlocking();

        String text;
        if (!isLockedUp)
            text = Res.get("dao.bond.table.notBonded");
        else if (!isUnlocked)
            text = Res.get("dao.bond.table.lockedUp");
        else if (isUnlocking)
            text = Res.get("dao.bond.table.unlocking");
        else
            text = Res.get("dao.bond.table.unlocked");

        label.setText(text);

        button.updateText(isLockedUp ? Res.get("dao.bond.table.button.revoke") : Res.get("dao.bond.table.button.lockup"));
        button.setVisible(!isLockedUp || !isUnlocked);
        button.setManaged(button.isVisible());

        //TODO listen to unconfirmed txs and update button and label state
    }


    // DaoStateListener
    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        update();
    }

    @Override
    public void onParseBlockChainComplete() {
    }
}
