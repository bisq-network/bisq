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

package io.bisq.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Utilities;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import org.controlsfx.control.PopOver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class FundsTextField extends AnchorPane {
    public static final Logger log = LoggerFactory.getLogger(FundsTextField.class);

    private final StringProperty amount = new SimpleStringProperty();
    private final StringProperty fundsStructure = new SimpleStringProperty();
    private final Label infoIcon;
    private Boolean hidePopover;
    private PopOver infoPopover;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    public FundsTextField() {

        TextField textField = new TextField();
        // might be removed if no styling is necessary
        textField.setId("amount-text-field");
        textField.setEditable(false);
        textField.setPromptText(Res.get("createOffer.fundsBox.totalsNeeded.prompt"));
        textField.textProperty().bind(Bindings.concat(amount, " ", fundsStructure));
        textField.setFocusTraversable(false);

        infoIcon = new Label();
        infoIcon.setLayoutY(3);
        infoIcon.getStyleClass().addAll("icon", "info");
        AwesomeDude.setIcon(infoIcon, AwesomeIcon.INFO_SIGN);

        Label copyIcon = new Label();
        copyIcon.setLayoutY(3);
        copyIcon.getStyleClass().addAll("icon", "highlight");
        Tooltip.install(copyIcon, new Tooltip(Res.get("shared.copyToClipboard")));
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        copyIcon.setOnMouseClicked(e -> {
            String text = getAmount();
            if (text != null && text.length() > 0) {
                String copyText;
                String[] strings = text.split(" ");
                if (strings.length > 1)
                    copyText = strings[0]; // exclude the BTC postfix
                else
                    copyText = text;

                Utilities.copyToClipboard(copyText);
            }
        });

        AnchorPane.setRightAnchor(copyIcon, 30.0);
        AnchorPane.setRightAnchor(infoIcon, 62.0);
        AnchorPane.setRightAnchor(textField, 55.0);
        AnchorPane.setLeftAnchor(textField, 0.0);

        getChildren().addAll(textField, infoIcon, copyIcon);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setContentForInfoPopOver(Node node) {
        // As we don't use binding here we need to recreate it on mouse over to reflect the current state
        infoIcon.setOnMouseEntered(e -> {
            hidePopover = false;
            showInfoPopOver(node);
        });
        infoIcon.setOnMouseExited(e -> {
            if (infoPopover != null)
                hidePopover = true;
                UserThread.runAfter(() -> {
                    if (hidePopover) {
                        infoPopover.hide();
                        hidePopover = false;
                    }
                },250, TimeUnit.MILLISECONDS);
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showInfoPopOver(Node node) {
        node.getStyleClass().add("default-text");

        if (infoPopover == null) infoPopover = new PopOver(node);

        if (infoIcon.getScene() != null) {
            infoPopover.setDetachable(false);
            infoPopover.setArrowLocation(PopOver.ArrowLocation.RIGHT_TOP);
            infoPopover.setArrowIndent(5);

            infoPopover.show(infoIcon, -17);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setAmount(String amount) {
        this.amount.set(amount);
    }

    public String getAmount() {
        return amount.get();
    }

    public StringProperty amountProperty() {
        return amount;
    }

    public void setFundsStructure(String fundsStructure) {
        this.fundsStructure.set(fundsStructure);
    }
}
