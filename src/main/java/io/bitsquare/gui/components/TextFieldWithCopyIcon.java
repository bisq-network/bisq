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

package io.bitsquare.gui.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextFieldWithCopyIcon extends AnchorPane {
    private static final Logger log = LoggerFactory.getLogger(TextFieldWithCopyIcon.class);

    private final StringProperty text = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TextFieldWithCopyIcon() {
        Label copyIcon = new Label();
        copyIcon.setLayoutY(3);
        copyIcon.getStyleClass().add("copy-icon");
        Tooltip.install(copyIcon, new Tooltip("Copy to clipboard"));
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        AnchorPane.setRightAnchor(copyIcon, 0.0);
        copyIcon.setOnMouseClicked(e -> {
            if (getText() != null && getText().length() > 0) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(getText());
                clipboard.setContent(content);
            }
        });
        TextField txIdLabel = new TextField();
        txIdLabel.setEditable(false);
        txIdLabel.textProperty().bindBidirectional(text);
        AnchorPane.setRightAnchor(txIdLabel, 30.0);
        AnchorPane.setLeftAnchor(txIdLabel, 0.0);
        txIdLabel.focusTraversableProperty().set(focusTraversableProperty().get());
        focusedProperty().addListener((ov, oldValue, newValue) -> txIdLabel.requestFocus());

        getChildren().addAll(txIdLabel, copyIcon);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getText() {
        return text.get();
    }

    public StringProperty textProperty() {
        return text;
    }

    public void setText(String text) {
        this.text.set(text);
    }

}
