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

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.TextAlignment;

import javafx.geometry.Pos;

import lombok.Getter;

public class TextFieldWithIcon extends AnchorPane {
    @Getter
    private final Label iconLabel;
    @Getter
    private final TextField textField;
    private final Label dummyTextField;

    public TextFieldWithIcon() {
        textField = new JFXTextField();
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        setLeftAnchor(textField, 0d);
        setRightAnchor(textField, 0d);

        dummyTextField = new Label();
        dummyTextField.setWrapText(true);
        dummyTextField.setAlignment(Pos.CENTER_LEFT);
        dummyTextField.setTextAlignment(TextAlignment.LEFT);
        dummyTextField.setMouseTransparent(true);
        dummyTextField.setFocusTraversable(false);
        setLeftAnchor(dummyTextField, 0d);
        dummyTextField.setVisible(false);

        iconLabel = new Label();
        iconLabel.setLayoutX(0);
        iconLabel.setLayoutY(3);

        dummyTextField.widthProperty().addListener((observable, oldValue, newValue) -> {
            iconLabel.setLayoutX(dummyTextField.widthProperty().get() + 20);
        });

        getChildren().addAll(textField, dummyTextField, iconLabel);
    }

    public void setIcon(AwesomeIcon iconLabel) {
        AwesomeDude.setIcon(this.iconLabel, iconLabel);
    }

    public void setText(String text) {
        textField.setText(text);
        dummyTextField.setText(text);
    }
}
