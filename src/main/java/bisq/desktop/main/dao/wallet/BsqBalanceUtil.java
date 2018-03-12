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

import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;

import bisq.common.locale.Res;

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
    private TextField availableBalanceTextField, unverifiedBalanceTextField, totalBalanceTextField;

    @Inject
    private BsqBalanceUtil(BsqWalletService bsqWalletService,
                           BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
    }

    public int addGroup(GridPane gridPane, int gridRow) {
        addTitledGroupBg(gridPane, gridRow, 3, Res.get("shared.balance"));
        availableBalanceTextField = addLabelTextField(gridPane, gridRow, Res.getWithCol("shared.availableBsqBalance"),
                Layout.FIRST_ROW_DISTANCE).second;
        availableBalanceTextField.setMouseTransparent(false);
        availableBalanceTextField.setMaxWidth(150);
        availableBalanceTextField.setAlignment(Pos.CENTER_RIGHT);

        unverifiedBalanceTextField = addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.unverifiedBsqBalance")).second;
        unverifiedBalanceTextField.setMouseTransparent(false);
        unverifiedBalanceTextField.setMaxWidth(availableBalanceTextField.getMaxWidth());
        unverifiedBalanceTextField.setAlignment(Pos.CENTER_RIGHT);

        totalBalanceTextField = addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.totalBsqBalance")).second;
        totalBalanceTextField.setMouseTransparent(false);
        totalBalanceTextField.setMaxWidth(availableBalanceTextField.getMaxWidth());
        totalBalanceTextField.setAlignment(Pos.CENTER_RIGHT);
        return gridRow;
    }

    public void activate() {
        updateAvailableBalance(bsqWalletService.getAvailableBalance(), bsqWalletService.getUnverifiedBalance());
        bsqWalletService.addBsqBalanceListener(this);
    }

    public void deactivate() {
        bsqWalletService.removeBsqBalanceListener(this);
    }

    @Override
    public void updateAvailableBalance(Coin availableBalance, Coin unverifiedBalance) {
        availableBalanceTextField.setText(bsqFormatter.formatCoinWithCode(availableBalance));
        unverifiedBalanceTextField.setText(bsqFormatter.formatCoinWithCode(unverifiedBalance));
        totalBalanceTextField.setText(bsqFormatter.formatCoinWithCode(availableBalance.add(unverifiedBalance)));
    }
}
