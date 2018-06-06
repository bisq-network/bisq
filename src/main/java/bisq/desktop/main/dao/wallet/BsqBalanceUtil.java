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

import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.geometry.Pos;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addLabelTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@Slf4j
public class BsqBalanceUtil implements BsqBalanceListener {
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private TextField confirmedBalanceTextField, pendingBalanceTextField, lockedForVoteBalanceTextField,
            totalBalanceTextField;

    @Inject
    private BsqBalanceUtil(BsqWalletService bsqWalletService,
                           BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
    }

    public int addGroup(GridPane gridPane, int gridRow) {
        addTitledGroupBg(gridPane, gridRow, 4, Res.get("shared.balance"));
        confirmedBalanceTextField = addLabelTextField(gridPane, gridRow, Res.getWithCol("shared.availableBsqBalance"),
                Layout.FIRST_ROW_DISTANCE).second;
        confirmedBalanceTextField.setMouseTransparent(false);
        confirmedBalanceTextField.setMaxWidth(150);
        confirmedBalanceTextField.setAlignment(Pos.CENTER_RIGHT);

        pendingBalanceTextField = addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.unverifiedBsqBalance")).second;
        pendingBalanceTextField.setMouseTransparent(false);
        pendingBalanceTextField.setMaxWidth(confirmedBalanceTextField.getMaxWidth());
        pendingBalanceTextField.setAlignment(Pos.CENTER_RIGHT);

        lockedForVoteBalanceTextField = addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared" +
                ".lockedForVoteBalance")).second;
        lockedForVoteBalanceTextField.setMouseTransparent(false);
        lockedForVoteBalanceTextField.setMaxWidth(confirmedBalanceTextField.getMaxWidth());
        lockedForVoteBalanceTextField.setAlignment(Pos.CENTER_RIGHT);

        totalBalanceTextField = addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.totalBsqBalance")).second;
        totalBalanceTextField.setMouseTransparent(false);
        totalBalanceTextField.setMaxWidth(confirmedBalanceTextField.getMaxWidth());
        totalBalanceTextField.setAlignment(Pos.CENTER_RIGHT);

        return gridRow;
    }

    public void activate() {
        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getPendingBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockedInBondsBalance());
        bsqWalletService.addBsqBalanceListener(this);
    }

    public void deactivate() {
        bsqWalletService.removeBsqBalanceListener(this);
    }


    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockedInBondsBalance) {
        confirmedBalanceTextField.setText(bsqFormatter.formatCoinWithCode(confirmedBalance));
        pendingBalanceTextField.setText(bsqFormatter.formatCoinWithCode(pendingBalance));
        lockedForVoteBalanceTextField.setText(bsqFormatter.formatCoinWithCode(lockedForVotingBalance));
        final Coin total = confirmedBalance.add(pendingBalance).add(lockedForVotingBalance).add(lockedInBondsBalance);
        totalBalanceTextField.setText(bsqFormatter.formatCoinWithCode(total));
    }
}
