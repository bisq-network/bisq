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
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TitledSeparator extends Pane {
    private static final Logger log = LoggerFactory.getLogger(TitledSeparator.class);

    private final Label label;
    private final StringProperty text = new SimpleStringProperty();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TitledSeparator() {
        GridPane.setMargin(this, new Insets(-10, -10, -10, -10));
        GridPane.setColumnSpan(this, 2);

        Separator separator = new Separator();
        separator.prefWidthProperty().bind(widthProperty());
        separator.setLayoutX(0);
        separator.setLayoutY(6);
        separator.setId("titled-separator");

        label = new Label();
        label.textProperty().bind(text);
        label.setLayoutX(8);
        label.setLayoutY(-8);
        label.setPadding(new Insets(0, 7, 0, 5));
        setActive();
        getChildren().addAll(separator, label);

    }

    public void setInactive() {
        setId("titled-group-bg");
        label.setId("titled-group-bg-label");
    }

    public void setActive() {
        setId("titled-group-bg-active");
        label.setId("titled-group-bg-label-active");
    }

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
