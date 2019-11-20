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

package bisq.desktop.main.dao.wallet.receive;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.BsqAddressTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.app.DevEnv;
import bisq.common.util.Tuple3;

import javax.inject.Inject;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import static bisq.desktop.util.FormBuilder.addLabelBsqAddressTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BsqReceiveView extends ActivatableView<GridPane, Void> {

    private BsqAddressTextField addressTextField;
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final BsqBalanceUtil bsqBalanceUtil;
    private int gridRow = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqReceiveView(BsqWalletService bsqWalletService, BsqFormatter bsqFormatter, BsqBalanceUtil bsqBalanceUtil) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.bsqBalanceUtil = bsqBalanceUtil;
    }

    @Override
    public void initialize() {
        if (DevEnv.isDaoActivated()) {
            gridRow = bsqBalanceUtil.addGroup(root, gridRow);

            TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 1,
                    Res.get("dao.wallet.receive.fundYourWallet"), Layout.GROUP_DISTANCE);
            titledGroupBg.getStyleClass().add("last");
            GridPane.setColumnSpan(titledGroupBg, 3);
            Tuple3<Label, BsqAddressTextField, VBox> tuple = addLabelBsqAddressTextField(root, gridRow,
                    Res.get("dao.wallet.receive.bsqAddress"),
                    Layout.FIRST_ROW_AND_GROUP_DISTANCE);
            addressTextField = tuple.second;
            GridPane.setColumnSpan(tuple.third, 3);
        }
    }

    @Override
    protected void activate() {
        if (DevEnv.isDaoActivated())
            bsqBalanceUtil.activate();

        addressTextField.setAddress(bsqFormatter.getBsqAddressStringFromAddress(bsqWalletService.getUnusedAddress()));
    }

    @Override
    protected void deactivate() {
        if (DevEnv.isDaoActivated())
            bsqBalanceUtil.deactivate();
    }
}

