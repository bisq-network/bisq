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
import bisq.desktop.main.dao.bonding.BondingViewUtils;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import javafx.scene.control.Label;

import java.util.Date;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode
class BondedRolesListItem implements DaoStateListener {
    @Getter
    private final BondedRole bondedRole;
    private final DaoFacade daoFacade;
    private final BondingViewUtils bondingViewUtils;
    private final BsqFormatter bsqFormatter;
    @Getter
    private final AutoTooltipButton button;
    @Getter
    private final Label label;
    @Getter
    private final Role role;

    private final boolean isMyRole;

    BondedRolesListItem(BondedRole bondedRole,
                        DaoFacade daoFacade,
                        BondingViewUtils bondingViewUtils,
                        BsqFormatter bsqFormatter) {
        this.bondedRole = bondedRole;
        this.daoFacade = daoFacade;
        this.bondingViewUtils = bondingViewUtils;
        this.bsqFormatter = bsqFormatter;

        role = bondedRole.getBondedAsset();
        isMyRole = daoFacade.isMyRole(role);

        daoFacade.addBsqStateListener(this);

        button = new AutoTooltipButton();
        button.setMinWidth(70);
        label = new Label();

        button.setOnAction(e -> {
            if (bondedRole.getBondState() == BondState.READY_FOR_LOCKUP) {
                bondingViewUtils.lockupBondForBondedRole(role,
                        txId -> {
                            bondedRole.setLockupTxId(txId);
                            bondedRole.setBondState(BondState.LOCKUP_TX_PENDING);
                            update();
                            button.setDisable(true);
                        });
            } else if (bondedRole.getBondState() == BondState.LOCKUP_TX_CONFIRMED) {
                bondingViewUtils.unLock(bondedRole.getLockupTxId(),
                        txId -> {
                            bondedRole.setUnlockTxId(txId);
                            bondedRole.setBondState(BondState.UNLOCK_TX_PENDING);
                            update();
                            button.setDisable(true);
                        });
            }
        });

        update();
    }

    public String getStartDate() {
        return bondedRole.getLockupDate() > 0 ?
                bsqFormatter.formatDateTime(new Date(bondedRole.getLockupDate())) :
                "-";
    }

    public String getRevokeDate() {
        return bondedRole.getUnlockDate() > 0 ?
                bsqFormatter.formatDateTime(new Date(bondedRole.getUnlockDate())) :
                "-";
    }

    public void cleanup() {
        daoFacade.removeBsqStateListener(this);
        button.setOnAction(null);
    }


    private void update() {
        label.setText(Res.get("dao.bond.bondState." + bondedRole.getBondState().name()));

        boolean showLockup = bondedRole.getBondState() == BondState.READY_FOR_LOCKUP;
        boolean showRevoke = bondedRole.getBondState() == BondState.LOCKUP_TX_CONFIRMED;
        if (showLockup)
            button.updateText(Res.get("dao.bond.table.button.lockup"));
        else if (showRevoke)
            button.updateText(Res.get("dao.bond.table.button.revoke"));


        boolean showButton = isMyRole && (showLockup || showRevoke);
        button.setVisible(showButton);
        button.setManaged(showButton);
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
