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

package io.bitsquare.gui.main.portfolio.pending.steps;

import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.Layout;

import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.ComponentBuilder.*;

public class CompletedView extends AnchorPane{
    private static final Logger log = LoggerFactory.getLogger(WaitView.class);

    private Label btcTradeAmountLabel;
    private TextField btcTradeAmountTextField;
    private Label fiatTradeAmountLabel;
    private TextField fiatTradeAmountTextField;
    private Label feesLabel;
    private TextField feesTextField;
    private Label securityDepositLabel;
    private TextField securityDepositTextField;
    private InfoDisplay infoDisplay;

    private InputTextField withdrawAddressTextField;
    private TextField withdrawAmountTextField;

    public CompletedView() {
        buildViews();
    }

    private void buildViews() {
        AnchorPane.setLeftAnchor(this, 0d);
        AnchorPane.setRightAnchor(this, 0d);
        AnchorPane.setTopAnchor(this, 0d);
        AnchorPane.setBottomAnchor(this, 0d);

        int i = 0;
        GridPane gridPane = getAndAddGridPane(this);

        getAndAddTitledGroupBg(gridPane, i, 5, "Summary");
        LabelTextFieldPair btcTradeAmountPair = getAndAddLabelTextFieldPair(gridPane, i++, "You have bought:", Layout.FIRST_ROW_DISTANCE);
        btcTradeAmountLabel = btcTradeAmountPair.label;
        btcTradeAmountTextField = btcTradeAmountPair.textField;

        LabelTextFieldPair fiatTradeAmountPair = getAndAddLabelTextFieldPair(gridPane, i++, "You have paid:");
        fiatTradeAmountLabel = fiatTradeAmountPair.label;
        fiatTradeAmountTextField = fiatTradeAmountPair.textField;

        LabelTextFieldPair feesPair = getAndAddLabelTextFieldPair(gridPane, i++, "Total fees paid:");
        feesLabel = feesPair.label;
        feesTextField = feesPair.textField;

        LabelTextFieldPair securityDepositPair = getAndAddLabelTextFieldPair(gridPane, i++, "Refunded security deposit:");
        securityDepositLabel = securityDepositPair.label;
        securityDepositTextField = securityDepositPair.textField;

        infoDisplay = getAndAddInfoDisplay(gridPane, i++, "infoDisplay", this::onOpenHelp);

        getAndAddTitledGroupBg(gridPane, i, 2, "Withdraw your bitcoins", Layout.GROUP_DISTANCE);
        withdrawAmountTextField = getAndAddLabelTextFieldPair(gridPane, i++, "Amount to withdraw:", Layout.FIRST_ROW_AND_GROUP_DISTANCE).textField;
        withdrawAddressTextField = getAndAddLabelInputTextFieldPair(gridPane, i++, "Withdraw to address:").inputTextField;
        getAndAddButton(gridPane, i++, "Withdraw to external wallet", this::onWithdraw);
    }

    private void onWithdraw(ActionEvent actionEvent) {
        log.debug("onWithdraw");
    }

    private void onOpenHelp(ActionEvent actionEvent) {
        log.debug("onOpenHelp");
    }

}
