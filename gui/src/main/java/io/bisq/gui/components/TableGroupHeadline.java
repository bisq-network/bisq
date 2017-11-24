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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TableGroupHeadline extends Pane {

    private final Label label;
    private final StringProperty text = new SimpleStringProperty();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TableGroupHeadline() {
        this("");
    }

    public TableGroupHeadline(String title) {
        text.set(title);

        GridPane.setMargin(this, new Insets(-10, -10, -10, -10));
        GridPane.setColumnSpan(this, 2);

        Pane bg = new StackPane();
        bg.setId("table-group-headline");
        bg.prefWidthProperty().bind(widthProperty());
        bg.prefHeightProperty().bind(heightProperty());

        label = new Label();
        label.textProperty().bind(text);
        label.setLayoutX(8);
        label.setPadding(new Insets(-8, 7, 0, 5));
        setActive();
        getChildren().addAll(bg, label);
    }

    public void setInactive() {
        setId("titled-group-bg");
        label.setId("titled-group-bg-label");
    }

    private void setActive() {
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
