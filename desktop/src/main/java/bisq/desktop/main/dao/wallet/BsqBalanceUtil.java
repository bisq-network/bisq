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
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

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
public class BsqBalanceUtil implements BsqBalanceListener {
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;

    // Displaying general BSQ info
    private TextField availableBalanceTextField, availableNonBsqBalanceTextField, unverifiedBalanceTextField, lockedForVoteBalanceTextField,
            lockedInBondsBalanceTextField, totalBalanceTextField;

    // Displaying bond dashboard info
    private TextField lockupAmountTextField, unlockingAmountTextField;
    private TitledGroupBg titledGroupBg;
    private Label availableNonBsqBalanceLabel;

    @Inject
    private BsqBalanceUtil(BsqWalletService bsqWalletService,
                           BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
    }

    public int addGroup(GridPane gridPane, int gridRow) {
        int startIndex = gridRow;
        titledGroupBg = addTitledGroupBg(gridPane, gridRow, 3, Res.get("dao.wallet.dashboard.myBalance"));
        availableBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, gridRow,
                Res.get("dao.availableBsqBalance"), Layout.FIRST_ROW_DISTANCE).second;
        unverifiedBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow,
                Res.get("dao.unverifiedBsqBalance")).second;
        totalBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow,
                Res.get("dao.totalBsqBalance")).second;

        gridRow = startIndex;
        int columnIndex = 2;
        titledGroupBg = addTitledGroupBg(gridPane, gridRow, columnIndex, 3, "");
        lockedForVoteBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, gridRow, columnIndex,
                Res.get("dao.lockedForVoteBalance"), Layout.FIRST_ROW_DISTANCE).second;
        lockedInBondsBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow, columnIndex,
                Res.get("dao.lockedInBonds")).second;
        Tuple3<Label, TextField, VBox> tuple3 = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow, columnIndex,
                Res.get("dao.availableNonBsqBalance"));

        // TODO add unlockingBondsBalanceTextField

        availableNonBsqBalanceLabel = tuple3.first;
        availableNonBsqBalanceTextField = tuple3.second;
        availableNonBsqBalanceTextField.setVisible(false);
        availableNonBsqBalanceTextField.setManaged(false);

        return gridRow;
    }

    public int addBondBalanceGroup(GridPane gridPane, int gridRow) {
        addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("dao.bonding.dashboard.bondsHeadline"), Layout.GROUP_DISTANCE);

        lockupAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, gridRow,
                Res.get("dao.bonding.dashboard.lockupAmount"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        unlockingAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow,
                Res.get("dao.bonding.dashboard.unlockingAmount")).second;

        return gridRow;
    }

    public void activate() {
        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());
        bsqWalletService.addBsqBalanceListener(this);
    }

    public void deactivate() {
        bsqWalletService.removeBsqBalanceListener(this);
    }


    @Override
    public void onUpdateBalances(Coin availableBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {

        boolean isNonBsqBalanceAvailable = availableNonBsqBalance.value > 0;

        availableBalanceTextField.setText(bsqFormatter.formatCoinWithCode(availableBalance));
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

        final Coin total = availableBalance
                .add(unverifiedBalance)
                .add(lockedForVotingBalance)
                .add(lockupBondsBalance)
                .add(unlockingBondsBalance);
        totalBalanceTextField.setText(bsqFormatter.formatCoinWithCode(total));
    }
}
