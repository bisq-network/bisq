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

import bisq.desktop.util.Layout;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addLabelTextField;
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

    @Inject
    private BsqBalanceUtil(BsqWalletService bsqWalletService,
                           BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
    }

    public int addGroup(GridPane gridPane, int gridRow) {
        addTitledGroupBg(gridPane, gridRow, 6, Res.get("shared.balance"));
        availableBalanceTextField = addLabelTextField(gridPane, gridRow,
                Res.getWithCol("dao.availableBsqBalance"),
                Layout.FIRST_ROW_DISTANCE).second;
        availableBalanceTextField.setMouseTransparent(false);

        availableNonBsqBalanceTextField = addLabelTextField(gridPane, ++gridRow,
                Res.getWithCol("dao.availableNonBsqBalance")).second;
        availableNonBsqBalanceTextField.setMouseTransparent(false);

        unverifiedBalanceTextField = addLabelTextField(gridPane, ++gridRow,
                Res.getWithCol("dao.unverifiedBsqBalance")).second;
        unverifiedBalanceTextField.setMouseTransparent(false);

        lockedForVoteBalanceTextField = addLabelTextField(gridPane, ++gridRow,
                Res.getWithCol("dao.lockedForVoteBalance")).second;
        lockedForVoteBalanceTextField.setMouseTransparent(false);

        lockedInBondsBalanceTextField = addLabelTextField(gridPane, ++gridRow, Res.getWithCol(
                "dao.lockedInBonds")).second;
        lockedInBondsBalanceTextField.setMouseTransparent(false);

        // TODO add unlockingBondsBalanceTextField

        totalBalanceTextField = addLabelTextField(gridPane, ++gridRow,
                Res.getWithCol("dao.totalBsqBalance")).second;
        totalBalanceTextField.setMouseTransparent(false);

        return gridRow;
    }

    public int addBondBalanceGroup(GridPane gridPane, int gridRow) {
        addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("dao.bonding.dashboard.bondsHeadline"), Layout.GROUP_DISTANCE);

        lockupAmountTextField = addLabelTextField(gridPane, gridRow,
                Res.get("dao.bonding.dashboard.lockupAmount"),
                Layout.FIRST_ROW_DISTANCE + Layout.GROUP_DISTANCE).second;
        lockupAmountTextField.setMouseTransparent(false);

        unlockingAmountTextField = addLabelTextField(gridPane, ++gridRow,
                Res.get("dao.bonding.dashboard.unlockingAmount")).second;
        unlockingAmountTextField.setMouseTransparent(false);

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
        availableBalanceTextField.setText(bsqFormatter.formatCoinWithCode(availableBalance));
        availableNonBsqBalanceTextField.setText(bsqFormatter.formatBtcSatoshi(availableNonBsqBalance.value));
        unverifiedBalanceTextField.setText(bsqFormatter.formatCoinWithCode(unverifiedBalance));
        lockedForVoteBalanceTextField.setText(bsqFormatter.formatCoinWithCode(lockedForVotingBalance));
        lockedInBondsBalanceTextField.setText(bsqFormatter.formatCoinWithCode(
                lockupBondsBalance.add(unlockingBondsBalance)));

        if (lockupAmountTextField != null && unlockingAmountTextField != null) {
            lockupAmountTextField.setText(bsqFormatter.formatCoinWithCode(lockupBondsBalance));
            unlockingAmountTextField.setText(bsqFormatter.formatCoinWithCode(unlockingBondsBalance));
        }

        final Coin total = availableBalance
                .add(unverifiedBalance)
                .add(lockedForVotingBalance)
                .add(lockupBondsBalance)
                .add(unlockingBondsBalance);
        totalBalanceTextField.setText(bsqFormatter.formatCoinWithCode(total));
    }
}
