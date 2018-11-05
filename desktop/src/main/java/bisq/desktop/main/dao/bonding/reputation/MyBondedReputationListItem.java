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

package bisq.desktop.main.dao.bonding.reputation;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.main.dao.bonding.BondingViewUtils;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.reputation.MyBondedReputation;
import bisq.core.dao.governance.bond.reputation.MyReputation;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javafx.scene.control.Label;

import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@Data
@Slf4j
class MyBondedReputationListItem implements DaoStateListener {
    private final MyBondedReputation myBondedReputation;
    private final DaoFacade daoFacade;
    private final BsqFormatter bsqFormatter;
    private final String hash, salt;
    private final String txId;
    private final String amount;
    private final String lockupDate;
    private final String lockTime;

    @Getter
    private final AutoTooltipButton button;
    @Getter
    private final Label stateLabel;

    MyBondedReputationListItem(MyBondedReputation myBondedReputation,
                               DaoFacade daoFacade,
                               BondingViewUtils bondingViewUtils,
                               BsqFormatter bsqFormatter) {
        this.myBondedReputation = myBondedReputation;
        this.daoFacade = daoFacade;
        this.bsqFormatter = bsqFormatter;

        MyReputation myReputation = myBondedReputation.getBondedAsset();
        hash = Utilities.bytesAsHexString(myReputation.getHash());
        salt = Utilities.bytesAsHexString(myReputation.getSalt());
        txId = myBondedReputation.getLockupTxId();
        amount = bsqFormatter.formatCoin(Coin.valueOf(myBondedReputation.getAmount()));
        lockupDate = bsqFormatter.formatDateTime(new Date(myBondedReputation.getLockupDate()));
        lockTime = Integer.toString(myBondedReputation.getLockTime());

        daoFacade.addBsqStateListener(this);

        button = new AutoTooltipButton();
        stateLabel = new Label();

        button.setOnAction(e -> {
            if (myBondedReputation.getBondState() == BondState.LOCKUP_TX_CONFIRMED) {
                bondingViewUtils.unLock(myBondedReputation.getLockupTxId(),
                        txId -> {
                            myBondedReputation.setUnlockTxId(txId);
                            myBondedReputation.setBondState(BondState.UNLOCK_TX_PENDING);
                            update();
                            button.setDisable(true);
                        });
            }
        });

        update();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

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

    void cleanup() {
        daoFacade.removeBsqStateListener(this);
        button.setOnAction(null);
    }

    private void update() {
        stateLabel.setText(Res.get("dao.bond.bondState." + myBondedReputation.getBondState().name()));

        boolean showButton = myBondedReputation.getBondState() == BondState.LOCKUP_TX_CONFIRMED;
        if (showButton)
            button.updateText(Res.get("dao.bond.table.button.unlock"));

        button.setVisible(showButton);
        button.setManaged(showButton);
    }
}
