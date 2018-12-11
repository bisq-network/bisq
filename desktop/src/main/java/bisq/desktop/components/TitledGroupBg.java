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

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

import javafx.geometry.Insets;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TitledGroupBg extends Pane {

    private final Label label;
    private final StringProperty text = new SimpleStringProperty();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TitledGroupBg() {
        GridPane.setMargin(this, new Insets(-10, -10, -10, -10));
        GridPane.setColumnSpan(this, 2);

        label = new AutoTooltipLabel();
        label.textProperty().bind(text);
        label.setLayoutX(4);
        label.setLayoutY(-8);
        label.setPadding(new Insets(0, 7, 0, 5));
        setActive();
        getChildren().add(label);
    }

    public void setInactive() {
        resetStyles();
        getStyleClass().add("titled-group-bg");
        label.getStyleClass().add("titled-group-bg-label");
    }

    private void resetStyles() {
        getStyleClass().removeAll("titled-group-bg", "titled-group-bg-active");
        label.getStyleClass().removeAll("titled-group-bg-label", "titled-group-bg-label-active");
    }

    private void setActive() {
        resetStyles();
        getStyleClass().add("titled-group-bg-active");
        label.getStyleClass().add("titled-group-bg-label-active");
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

    public Label getLabel() {
        return label;
    }

}
