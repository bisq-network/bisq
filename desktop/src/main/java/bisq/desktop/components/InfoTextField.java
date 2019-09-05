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

import bisq.common.UserThread;

import de.jensd.fx.fontawesome.AwesomeIcon;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;

import static bisq.desktop.util.FormBuilder.getIcon;
import static bisq.desktop.util.FormBuilder.getRegularIconForLabel;

public class InfoTextField extends AnchorPane {
    public static final Logger log = LoggerFactory.getLogger(InfoTextField.class);

    @Getter
    protected final JFXTextField textField;

    private final StringProperty text = new SimpleStringProperty();
    protected final Label infoIcon;
    private Label currentIcon;
    private PopOverWrapper popoverWrapper = new PopOverWrapper();
    private PopOver.ArrowLocation arrowLocation;

    public InfoTextField() {

        arrowLocation = PopOver.ArrowLocation.RIGHT_TOP;
        textField = new BisqTextField();
        textField.setLabelFloat(true);
        textField.setEditable(false);
        textField.textProperty().bind(text);
        textField.setFocusTraversable(false);
        textField.setId("info-field");

        infoIcon = getIcon(AwesomeIcon.INFO_SIGN);
        infoIcon.setLayoutY(5);
        infoIcon.getStyleClass().addAll("icon", "info");

        AnchorPane.setRightAnchor(infoIcon, 7.0);
        AnchorPane.setRightAnchor(textField, 0.0);
        AnchorPane.setLeftAnchor(textField, 0.0);

        hideIcons();

        getChildren().addAll(textField, infoIcon);
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setContentForInfoPopOver(Node node) {

        currentIcon = infoIcon;

        hideIcons();
        setActionHandlers(node);
    }

    public void setContent(MaterialDesignIcon icon, String info, String style, double opacity) {
        hideIcons();

        currentIcon = new Label();
        Text textIcon = getRegularIconForLabel(icon, currentIcon);

        setActionHandlers(new Label(info));

        currentIcon.setLayoutY(5);
        textIcon.getStyleClass().addAll("icon", style);
        currentIcon.setOpacity(opacity);
        AnchorPane.setRightAnchor(currentIcon, 7.0);

        getChildren().add(currentIcon);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////
    private void hideIcons() {
        infoIcon.setManaged(false);
        infoIcon.setVisible(false);
    }

    private void setActionHandlers(Node node) {

        currentIcon.setManaged(true);
        currentIcon.setVisible(true);

        // As we don't use binding here we need to recreate it on mouse over to reflect the current state
        currentIcon.setOnMouseEntered(e -> popoverWrapper.showPopOver(() -> createPopOver(node)));
        currentIcon.setOnMouseExited(e -> popoverWrapper.hidePopOver());
    }

    private PopOver createPopOver(Node node) {
        node.getStyleClass().add("default-text");

        PopOver popover = new PopOver(node);
        if (currentIcon.getScene() != null) {
            popover.setDetachable(false);
            popover.setArrowLocation(arrowLocation);
            popover.setArrowIndent(5);

            popover.show(currentIcon, -17);
        }
        return popover;
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
