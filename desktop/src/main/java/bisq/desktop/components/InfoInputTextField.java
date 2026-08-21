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

import bisq.desktop.components.controlsfx.control.PopOver;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import lombok.Getter;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class InfoInputTextField extends AnchorPane {
    private final StringProperty text = new SimpleStringProperty();
    @Getter
    private final InputTextField inputTextField;
    private final Label icon;
    private final PopOverWrapper popoverWrapper = new PopOverWrapper();
    @Nullable
    private Node node;

    public InfoInputTextField() {
        this(0);
    }

    public InfoInputTextField(double inputLineExtension) {
        super();

        inputTextField = new InputTextField(inputLineExtension);
        AnchorPane.setRightAnchor(inputTextField, 0.0);
        AnchorPane.setLeftAnchor(inputTextField, 0.0);

        icon = new Label();
        icon.setLayoutY(3);
        AnchorPane.setLeftAnchor(icon, 7.0);
        icon.setOnMouseEntered(e -> {
            if (node != null) {
                popoverWrapper.showPopOver(() -> checkNotNull(createPopOver()));
            }
        });
        icon.setOnMouseExited(e -> {
            if (node != null) {
                popoverWrapper.hidePopOver();
            }
        });

        hideIcon();

        getChildren().addAll(inputTextField, icon);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setContentForInfoPopOver(Node node) {
        setContentForPopOver(node, AwesomeIcon.INFO_SIGN);
    }

    public void setContentForWarningPopOver(Node node) {
        setContentForPopOver(node, AwesomeIcon.WARNING_SIGN, "warning");
    }

    public void setContentForPrivacyPopOver(Node node) {
        setContentForPopOver(node, AwesomeIcon.EYE_CLOSE);
    }

    public void setContentForPopOver(Node node, AwesomeIcon awesomeIcon) {
        setContentForPopOver(node, awesomeIcon, null);
    }

    public void setContentForPopOver(Node node, AwesomeIcon awesomeIcon, @Nullable String style) {
        this.node = node;
        AwesomeDude.setIcon(icon, awesomeIcon);
        icon.getStyleClass().removeAll("icon", "info", "warning", style);
        icon.getStyleClass().addAll("icon", style == null ? "info" : style);
        icon.setManaged(true);
        icon.setVisible(true);
    }

    public void hideIcon() {
        icon.setManaged(false);
        icon.setVisible(false);
    }

    public void setIconsRightAligned() {
        AnchorPane.clearConstraints(icon);
        AnchorPane.clearConstraints(inputTextField);

        AnchorPane.setRightAnchor(icon, 7.0);
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

    public StringProperty textProperty() {
        return text;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PopOver createPopOver() {
        if (node == null) {
            return null;
        }

        node.getStyleClass().add("default-text");
        PopOver popover = new PopOver(node);
        if (icon.getScene() != null) {
            popover.setDetachable(false);
            popover.setArrowLocation(PopOver.ArrowLocation.LEFT_TOP);
            popover.setArrowIndent(5);
            popover.show(icon, -17);
        }
        return popover;
    }
}
