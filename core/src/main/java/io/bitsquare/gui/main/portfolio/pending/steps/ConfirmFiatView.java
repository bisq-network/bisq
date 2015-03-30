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
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.util.Layout;

import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.ComponentBuilder.*;

public class ConfirmFiatView extends AnchorPane {
    private static final Logger log = LoggerFactory.getLogger(ConfirmFiatView.class);

    private TextField statusTextField;
    private TxIdTextField txIdTextField;
    private InfoDisplay infoDisplay;

    public ConfirmFiatView() {
        buildViews();
    }

    private void buildViews() {
        AnchorPane.setLeftAnchor(this, 0d);
        AnchorPane.setRightAnchor(this, 0d);
        AnchorPane.setTopAnchor(this, 0d);
        AnchorPane.setBottomAnchor(this, 0d);

        int i = 0;
        GridPane gridPane = getAndAddGridPane(this);
        getAndAddTitledGroupBg(gridPane, i, 3, "Trade status");
        statusTextField = getAndAddLabelTextFieldPair(gridPane, i++, "Status:", Layout.FIRST_ROW_DISTANCE).textField;
        txIdTextField = getAndAddLabelTxIdTextFieldPair(gridPane, i++, "Deposit transaction ID:").txIdTextField;
        infoDisplay = getAndAddInfoDisplay(gridPane, i++, "infoDisplay", this::onOpenHelp);
        getAndAddButton(gridPane, i++, "Confirm payment receipt", this::onPaymentReceived);
    }

    private void onPaymentReceived(ActionEvent actionEvent) {
        log.debug("onPaymentReceived");
    }

    private void onOpenHelp(ActionEvent actionEvent) {
        log.debug("onOpenHelp");
    }

}


