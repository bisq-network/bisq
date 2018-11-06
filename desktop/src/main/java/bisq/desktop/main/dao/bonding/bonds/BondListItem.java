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

import bisq.desktop.main.dao.bonding.BondingViewUtils;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.bond.role.BondedRolesService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javafx.scene.control.Button;
import javafx.scene.control.Label;

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
    private final String bondType;
    private final String txId;
    private final String amount;
    private final String lockupDate;
    private final String lockTime;
    private final Label stateLabel;
    private final String bondDetails;

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


        amount = bsqFormatter.formatCoin(Coin.valueOf(bond.getAmount()));
        lockTime = Integer.toString(bond.getLockTime());
        if (bond instanceof BondedRole) {
            bondType = Res.get("dao.bond.bondedRoles");
            bondDetails = bond.getBondedAsset().getDisplayString();
        } else {
            bondType = Res.get("dao.bond.bondedReputation");
            bondDetails = Utilities.bytesAsHexString(bond.getBondedAsset().getHash());
        }
        lockupDate = bsqFormatter.formatDateTime(new Date(bond.getLockupDate()));
        txId = bond.getLockupTxId();

        stateLabel = new Label();

        daoFacade.addBsqStateListener(this);
        update();
    }

    private void update() {
        stateLabel.setText(Res.get("dao.bond.bondState." + bond.getBondState().name()));
    }

    public void cleanup() {
        daoFacade.removeBsqStateListener(this);
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
