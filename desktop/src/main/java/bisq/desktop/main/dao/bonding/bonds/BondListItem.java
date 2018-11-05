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

package bisq.desktop.main.dao.bonding.bonds;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.main.dao.bonding.BondingViewUtils;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.role.BondedRolesService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import javafx.scene.control.Button;

import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@Data
@Slf4j
class BondListItem implements DaoStateListener {
    private final Bond bond;
    private final DaoFacade daoFacade;
    private final BondedRolesService bondedRolesService;
    private final BondingViewUtils bondingViewUtils;
    private final BsqFormatter bsqFormatter;
    private final String info;
    private final String txId;
    private final String amount;
    private final String lockTime;

    @Getter
    private Button button;

    BondListItem(Bond bond,
                 DaoFacade daoFacade,
                 BondedRolesService bondedRolesService,
                 BondingViewUtils bondingViewUtils,
                 BsqFormatter bsqFormatter) {
        this.bond = bond;
        this.daoFacade = daoFacade;
        this.bondedRolesService = bondedRolesService;
        this.bondingViewUtils = bondingViewUtils;
        this.bsqFormatter = bsqFormatter;


        info = bond.getBondedAsset().getDisplayString();
        txId = bond.getLockupTxId();
        amount = bsqFormatter.formatCoin(Coin.valueOf(bond.getAmount()));
        lockTime = bsqFormatter.formatDateTime(new Date(bond.getLockupDate()));

        button = new AutoTooltipButton();
        button.setMinWidth(70);
        // label = new Label();
/*
        daoFacade.addBsqStateListener(this);
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
        });*/
    }

    private void update() {
      /*  label.setText(Res.get("dao.bond.bondState." + bondedRole.getBondState().name()));

        boolean showLockup = bondedRole.getBondState() == BondState.READY_FOR_LOCKUP;
        boolean showRevoke = bondedRole.getBondState() == BondState.LOCKUP_TX_CONFIRMED;
        if (showLockup)
            button.updateText(Res.get("dao.bond.table.button.lockup"));
        else if (showRevoke)
            button.updateText(Res.get("dao.bond.table.button.revoke"));


        boolean showButton = isMyRole && (showLockup || showRevoke);
        button.setVisible(showButton);
        button.setManaged(showButton);*/
    }

    public void cleanup() {
        //  daoFacade.removeBsqStateListener(this);
        // button.setOnAction(null);
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
