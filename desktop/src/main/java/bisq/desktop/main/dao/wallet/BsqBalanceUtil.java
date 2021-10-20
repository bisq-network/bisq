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

package bisq.desktop.main.dao.wallet;

import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@Slf4j
public class BsqBalanceUtil implements BsqBalanceListener, DaoStateListener {
    private final BsqWalletService bsqWalletService;
    private final DaoStateService daoStateService;
    private final BsqFormatter bsqFormatter;
    private final DaoFacade daoFacade;

    // Displaying general BSQ info
    private TextField availableBalanceTextField, verifiedBalanceTextField, availableNonBsqBalanceTextField,
            unverifiedBalanceTextField, lockedForVoteBalanceTextField,
            lockedInBondsBalanceTextField, unconfirmedChangTextField, reputationBalanceTextField;

    // Displaying bond dashboard info
    private TextField lockupAmountTextField, unlockingAmountTextField;
    private Label availableNonBsqBalanceLabel;

    @Inject
    private BsqBalanceUtil(BsqWalletService bsqWalletService,
                           DaoStateService daoStateService,
                           BsqFormatter bsqFormatter,
                           DaoFacade daoFacade) {
        this.bsqWalletService = bsqWalletService;
        this.daoStateService = daoStateService;
        this.bsqFormatter = bsqFormatter;
        this.daoFacade = daoFacade;
    }


    public void activate() {
        bsqWalletService.addBsqBalanceListener(this);
        daoStateService.addDaoStateListener(this);
        triggerUpdate();
    }

    public void deactivate() {
        bsqWalletService.removeBsqBalanceListener(this);
        daoStateService.removeDaoStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        bsqWalletService.addBsqBalanceListener(this);
        triggerUpdate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBalanceListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin availableBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin unconfirmedChangeBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        boolean isNonBsqBalanceAvailable = availableNonBsqBalance.value > 0;

        availableBalanceTextField.setText(bsqFormatter.formatCoinWithCode(availableBalance));
        Coin verified = availableBalance.subtract(unconfirmedChangeBalance);
        verifiedBalanceTextField.setText(bsqFormatter.formatCoinWithCode(verified));
        unconfirmedChangTextField.setText(bsqFormatter.formatCoinWithCode(unconfirmedChangeBalance));
        unverifiedBalanceTextField.setText(bsqFormatter.formatCoinWithCode(unverifiedBalance));

        lockedForVoteBalanceTextField.setText(bsqFormatter.formatCoinWithCode(lockedForVotingBalance));
        lockedInBondsBalanceTextField.setText(bsqFormatter.formatCoinWithCode(
                lockupBondsBalance.add(unlockingBondsBalance)));
        if (lockupAmountTextField != null && unlockingAmountTextField != null) {
            lockupAmountTextField.setText(bsqFormatter.formatCoinWithCode(lockupBondsBalance));
            unlockingAmountTextField.setText(bsqFormatter.formatCoinWithCode(unlockingBondsBalance));
        }

        availableNonBsqBalanceLabel.setVisible(isNonBsqBalanceAvailable);
        availableNonBsqBalanceLabel.setManaged(isNonBsqBalanceAvailable);
        availableNonBsqBalanceTextField.setVisible(isNonBsqBalanceAvailable);
        availableNonBsqBalanceTextField.setManaged(isNonBsqBalanceAvailable);
        availableNonBsqBalanceTextField.setText(bsqFormatter.formatBTCWithCode(availableNonBsqBalance.value));
        String bsqSatoshi = bsqFormatter.formatBSQSatoshisWithCode(daoFacade.getAvailableMerit());
        reputationBalanceTextField.setText(bsqSatoshi);
    }


    private void triggerUpdate() {
        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getUnconfirmedChangeBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int addGroup(GridPane gridPane, int gridRow) {
        int startIndex = gridRow;
        addTitledGroupBg(gridPane, gridRow, 4, Res.get("dao.wallet.dashboard.myBalance"));
        availableBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, gridRow,
                Res.get("dao.availableBsqBalance"), Layout.FIRST_ROW_DISTANCE).second;
        verifiedBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow,
                Res.get("dao.verifiedBsqBalance")).second;
        unconfirmedChangTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow,
                Res.get("dao.unconfirmedChangeBalance")).second;
        unverifiedBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow,
                Res.get("dao.unverifiedBsqBalance")).second;

        gridRow = startIndex;
        int columnIndex = 2;
        addTitledGroupBg(gridPane, gridRow, columnIndex, 4, "");
        lockedForVoteBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, gridRow, columnIndex,
                Res.get("dao.lockedForVoteBalance"), Layout.FIRST_ROW_DISTANCE).second;
        lockedInBondsBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow, columnIndex,
                Res.get("dao.lockedInBonds")).second;
        reputationBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow, columnIndex,
                Res.get("dao.reputationBalance")).second;
        Tuple3<Label, TextField, VBox> tuple3 = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow, columnIndex,
                Res.get("dao.availableNonBsqBalance"));
        // Match left column
        ++gridRow;

        // TODO add unlockingBondsBalanceTextField

        availableNonBsqBalanceLabel = tuple3.first;
        availableNonBsqBalanceTextField = tuple3.second;
        availableNonBsqBalanceTextField.setVisible(false);
        availableNonBsqBalanceTextField.setManaged(false);

        return gridRow;
    }

    public int addBondBalanceGroup(GridPane gridPane, int gridRow, String groupStyle) {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("dao.bond.dashboard.bondsHeadline"), Layout.GROUP_DISTANCE);

        if (groupStyle != null) titledGroupBg.getStyleClass().add(groupStyle);

        lockupAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, gridRow,
                Res.get("dao.bond.dashboard.lockupAmount"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        unlockingAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow,
                Res.get("dao.bond.dashboard.unlockingAmount")).second;

        return gridRow;
    }
}
