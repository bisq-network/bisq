/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.dao.wallet.dashboard;

import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.dao.wallet.BalanceUtil;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.SQUFormatter;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;
import static io.bitsquare.gui.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class TokenDashboardView extends ActivatableView<GridPane, Void> {

    private TextField balanceTextField;

    private final SquWalletService squWalletService;
    private final SQUFormatter formatter;
    private BalanceUtil balanceUtil;

    private final int gridRow = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TokenDashboardView(SquWalletService squWalletService, SQUFormatter formatter, BalanceUtil balanceUtil) {
        this.squWalletService = squWalletService;
        this.formatter = formatter;

        this.balanceUtil = balanceUtil;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 1, "Balance");
        balanceTextField = addLabelTextField(root, gridRow, "BSQ balance:", Layout.FIRST_ROW_DISTANCE).second;
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

