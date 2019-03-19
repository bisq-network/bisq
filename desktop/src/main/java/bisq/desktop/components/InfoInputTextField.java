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

import bisq.common.UserThread;

import de.jensd.fx.fontawesome.AwesomeIcon;

import org.controlsfx.control.PopOver;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.concurrent.TimeUnit;

import lombok.Getter;

import static bisq.desktop.util.FormBuilder.getIcon;

public class InfoInputTextField extends AnchorPane {

    private final StringProperty text = new SimpleStringProperty();

    @Getter
    private final InputTextField inputTextField;
    @Getter
    private final Label infoIcon;
    @Getter
    private final Label warningIcon;
    @Getter
    private final Label privacyIcon;

    private Label currentIcon;
    private PopOver popover;
    private boolean hidePopover;

    public InfoInputTextField() {
        this(0);
    }

    public InfoInputTextField(double inputLineExtension) {
        super();

        inputTextField = new InputTextField(inputLineExtension);

        infoIcon = getIcon(AwesomeIcon.INFO_SIGN);
        infoIcon.setLayoutY(3);
        infoIcon.getStyleClass().addAll("icon", "info");

        warningIcon = getIcon(AwesomeIcon.WARNING_SIGN);
        warningIcon.setLayoutY(3);
        warningIcon.getStyleClass().addAll("icon", "warning");

        privacyIcon = getIcon(AwesomeIcon.EYE_CLOSE);
        privacyIcon.setLayoutY(3);
        privacyIcon.getStyleClass().addAll("icon", "info");

        AnchorPane.setLeftAnchor(infoIcon, 7.0);
        AnchorPane.setLeftAnchor(warningIcon, 7.0);
        AnchorPane.setLeftAnchor(privacyIcon, 7.0);
        AnchorPane.setRightAnchor(inputTextField, 0.0);
        AnchorPane.setLeftAnchor(inputTextField, 0.0);

        hideIcons();

        getChildren().addAll(inputTextField, infoIcon, warningIcon, privacyIcon);
    }


    private void hideIcons() {
        infoIcon.setManaged(false);
        infoIcon.setVisible(false);
        warningIcon.setManaged(false);
        warningIcon.setVisible(false);
        privacyIcon.setManaged(false);
        privacyIcon.setVisible(false);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setContentForInfoPopOver(Node node) {
        currentIcon = infoIcon;

        hideIcons();
        setActionHandlers(node);
    }

    public void setContentForWarningPopOver(Node node) {
        currentIcon = warningIcon;

        hideIcons();
        setActionHandlers(node);
    }

    public void setContentForPrivacyPopOver(Node node) {
        currentIcon = privacyIcon;

        hideIcons();
        setActionHandlers(node);
    }

    public void setIconsRightAligned() {
        AnchorPane.clearConstraints(infoIcon);
        AnchorPane.clearConstraints(warningIcon);
        AnchorPane.clearConstraints(privacyIcon);
        AnchorPane.clearConstraints(inputTextField);

        AnchorPane.setRightAnchor(infoIcon, 7.0);
        AnchorPane.setRightAnchor(warningIcon, 7.0);
        AnchorPane.setRightAnchor(privacyIcon, 7.0);
        AnchorPane.setLeftAnchor(inputTextField, 0.0);
        AnchorPane.setRightAnchor(inputTextField, 0.0);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setText(String text) {
        this.text.set(text);
    }

    public String getText() {
        return text.get();
    }

    public final StringProperty textProperty() {
        return text;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setActionHandlers(Node node) {

        if (node != null) {
            currentIcon.setManaged(true);
            currentIcon.setVisible(true);

            // As we don't use binding here we need to recreate it on mouse over to reflect the current state
            currentIcon.setOnMouseEntered(e -> {
                hidePopover = false;
                showPopOver(node);
            });
            currentIcon.setOnMouseExited(e -> {
                if (popover != null)
                    popover.hide();
                hidePopover = true;
                UserThread.runAfter(() -> {
                    if (hidePopover) {
                        popover.hide();
                        hidePopover = false;
                    }
                }, 250, TimeUnit.MILLISECONDS);
            });
        }
    }

    private void showPopOver(Node node) {
        node.getStyleClass().add("default-text");

        popover = new PopOver(node);
        if (currentIcon.getScene() != null) {
            popover.setDetachable(false);
            popover.setArrowLocation(PopOver.ArrowLocation.LEFT_TOP);
            popover.setArrowIndent(5);

            popover.show(currentIcon, -17);
        }
    }
}
