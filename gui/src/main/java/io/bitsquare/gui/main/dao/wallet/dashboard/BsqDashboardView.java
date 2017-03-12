/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.dao.wallet.dashboard;

import io.bitsquare.btc.wallet.BsqWalletService;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.dao.wallet.BalanceUtil;
import io.bitsquare.gui.util.BsqFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.Res;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;
import static io.bitsquare.gui.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BsqDashboardView extends ActivatableView<GridPane, Void> {

    private TextField balanceTextField;

    private final BsqWalletService bsqWalletService;
    private final BsqFormatter formatter;
    private BalanceUtil balanceUtil;

    private final int gridRow = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqDashboardView(BsqWalletService bsqWalletService, BsqFormatter formatter, BalanceUtil balanceUtil) {
        this.bsqWalletService = bsqWalletService;
        this.formatter = formatter;

        this.balanceUtil = balanceUtil;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 1, Res.get("shared.balance"));
        balanceTextField = addLabelTextField(root, gridRow, Res.get("shared.bsqBalance"), Layout.FIRST_ROW_DISTANCE).second;
        balanceUtil.setBalanceTextField(balanceTextField);
        balanceUtil.initialize();
    }

    @Override
    protected void activate() {
        balanceUtil.activate();
    }

    @Override
    protected void deactivate() {
        balanceUtil.deactivate();
    }
}

