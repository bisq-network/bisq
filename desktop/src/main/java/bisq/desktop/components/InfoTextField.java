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

import com.jfoenix.controls.JFXTextField;

import org.controlsfx.control.PopOver;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;

import static bisq.desktop.util.FormBuilder.getIcon;

public class InfoTextField extends AnchorPane {
    public static final Logger log = LoggerFactory.getLogger(InfoTextField.class);

    @Getter
    protected final JFXTextField textField;

    private final StringProperty text = new SimpleStringProperty();
    protected final Label infoIcon;
    protected final Label privacyIcon;
    private Label currentIcon;
    private Boolean hidePopover;
    private PopOver popover;
    private PopOver.ArrowLocation arrowLocation;

    public InfoTextField() {

        arrowLocation = PopOver.ArrowLocation.RIGHT_TOP;;
        textField = new BisqTextField();
        textField.setLabelFloat(true);
        textField.setEditable(false);
        textField.textProperty().bind(text);
        textField.setFocusTraversable(false);
        textField.setId("info-field");

        infoIcon = getIcon(AwesomeIcon.INFO_SIGN);
        infoIcon.setLayoutY(5);
        infoIcon.getStyleClass().addAll("icon", "info");

        privacyIcon = getIcon(AwesomeIcon.EYE_CLOSE);
        privacyIcon.setLayoutY(5);
        privacyIcon.getStyleClass().addAll("icon", "info");

        AnchorPane.setRightAnchor(infoIcon, 7.0);
        AnchorPane.setRightAnchor(privacyIcon, 7.0);
        AnchorPane.setRightAnchor(textField, 0.0);
        AnchorPane.setLeftAnchor(textField, 0.0);

        hideIcons();

        getChildren().addAll(textField, infoIcon, privacyIcon);
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setContentForInfoPopOver(Node node) {

        currentIcon = infoIcon;

        hideIcons();
        setActionHandlers(node);
    }

    public void setContentForPrivacyPopOver(Node node) {
        currentIcon = privacyIcon;

        hideIcons();
        setActionHandlers(node);
    }

    public void setIconsLeftAligned() {
        arrowLocation = PopOver.ArrowLocation.LEFT_TOP;;

        AnchorPane.clearConstraints(infoIcon);
        AnchorPane.clearConstraints(privacyIcon);

        AnchorPane.setLeftAnchor(infoIcon, 7.0);
        AnchorPane.setLeftAnchor(privacyIcon, 7.0);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////
    private void hideIcons() {
        infoIcon.setManaged(false);
        infoIcon.setVisible(false);
        privacyIcon.setManaged(false);
        privacyIcon.setVisible(false);
    }

    private void setActionHandlers(Node node) {

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

    private void showPopOver(Node node) {
        node.getStyleClass().add("default-text");

        popover = new PopOver(node);
        if (currentIcon.getScene() != null) {
            popover.setDetachable(false);
            popover.setArrowLocation(arrowLocation);
            popover.setArrowIndent(5);

            popover.show(currentIcon, -17);
        }
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

    public StringProperty textProperty() {
        return text;
    }
}
