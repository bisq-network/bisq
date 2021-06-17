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

import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;
import bisq.core.user.BlockChainExplorer;
import bisq.core.user.Preferences;

import bisq.common.util.Utilities;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

public class ExplorerAddressTextField extends AnchorPane {
    @Setter
    private static Preferences preferences;

    @Getter
    private final TextField textField;
    private final Label copyIcon, blockExplorerIcon, missingAddressWarningIcon;
    @Setter
    private boolean isBsq;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ExplorerAddressTextField() {
        copyIcon = new Label();
        copyIcon.setLayoutY(3);
        copyIcon.getStyleClass().addAll("icon", "highlight");
        copyIcon.setTooltip(new Tooltip(Res.get("explorerAddressTextField.copyToClipboard")));
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        AnchorPane.setRightAnchor(copyIcon, 30.0);

        Tooltip tooltip = new Tooltip(Res.get("explorerAddressTextField.blockExplorerIcon.tooltip"));

        blockExplorerIcon = new Label();
        blockExplorerIcon.getStyleClass().addAll("icon", "highlight");
        blockExplorerIcon.setTooltip(tooltip);
        AwesomeDude.setIcon(blockExplorerIcon, AwesomeIcon.EXTERNAL_LINK);
        blockExplorerIcon.setMinWidth(20);
        AnchorPane.setRightAnchor(blockExplorerIcon, 52.0);
        AnchorPane.setTopAnchor(blockExplorerIcon, 4.0);

        missingAddressWarningIcon = new Label();
        missingAddressWarningIcon.getStyleClass().addAll("icon", "error-icon");
        AwesomeDude.setIcon(missingAddressWarningIcon, AwesomeIcon.WARNING_SIGN);
        missingAddressWarningIcon.setTooltip(new Tooltip(Res.get("explorerAddressTextField.missingTx.warning.tooltip")));
        missingAddressWarningIcon.setMinWidth(20);
        AnchorPane.setRightAnchor(missingAddressWarningIcon, 52.0);
        AnchorPane.setTopAnchor(missingAddressWarningIcon, 4.0);
        missingAddressWarningIcon.setVisible(false);
        missingAddressWarningIcon.setManaged(false);

        textField = new JFXTextField();
        textField.setId("address-text-field");
        textField.setEditable(false);
        textField.setTooltip(tooltip);
        AnchorPane.setRightAnchor(textField, 80.0);
        AnchorPane.setLeftAnchor(textField, 0.0);
        textField.focusTraversableProperty().set(focusTraversableProperty().get());
        getChildren().addAll(textField, missingAddressWarningIcon, blockExplorerIcon, copyIcon);
    }

    public void setup(@Nullable String address) {
        if (address == null) {
            textField.setText(Res.get("shared.na"));
            textField.setId("address-text-field-error");
            blockExplorerIcon.setVisible(false);
            blockExplorerIcon.setManaged(false);
            copyIcon.setVisible(false);
            copyIcon.setManaged(false);
            missingAddressWarningIcon.setVisible(true);
            missingAddressWarningIcon.setManaged(true);
            return;
        }

        textField.setText(address);
        textField.setOnMouseClicked(mouseEvent -> openBlockExplorer(address));
        blockExplorerIcon.setOnMouseClicked(mouseEvent -> openBlockExplorer(address));
        copyIcon.setOnMouseClicked(e -> Utilities.copyToClipboard(address));
    }

    public void cleanup() {
        textField.setOnMouseClicked(null);
        blockExplorerIcon.setOnMouseClicked(null);
        copyIcon.setOnMouseClicked(null);
        textField.setText("");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void openBlockExplorer(String address) {
        if (preferences != null) {
            BlockChainExplorer blockChainExplorer = isBsq ?
                    preferences.getBsqBlockChainExplorer() :
                    preferences.getBlockChainExplorer();
            GUIUtil.openWebPage(blockChainExplorer.addressUrl + address, false);
        }
    }
}
