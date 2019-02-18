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

package bisq.desktop.components;

import bisq.core.locale.Res;

import bisq.common.util.Utilities;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.getIcon;

public class FundsTextField extends InfoTextField {
    public static final Logger log = LoggerFactory.getLogger(FundsTextField.class);

    private final StringProperty fundsStructure = new SimpleStringProperty();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    public FundsTextField() {
        super();
        textField.textProperty().unbind();
        textField.textProperty().bind(Bindings.concat(textProperty(), " ", fundsStructure));

        Label copyIcon = getIcon(AwesomeIcon.COPY);
        copyIcon.setLayoutY(5);
        copyIcon.getStyleClass().addAll("icon", "highlight");
        Tooltip.install(copyIcon, new Tooltip(Res.get("shared.copyToClipboard")));
        copyIcon.setOnMouseClicked(e -> {
            String text = getText();
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

        getChildren().add(copyIcon);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setFundsStructure(String fundsStructure) {
        this.fundsStructure.set(fundsStructure);
    }
}
