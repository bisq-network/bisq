/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.components;

import io.bisq.gui.main.MainView;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

public class TitledGroupBg extends Pane {

    private final Label label;
    private final StringProperty text = new SimpleStringProperty();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TitledGroupBg() {
        GridPane.setMargin(this, new Insets(MainView.scale(-10), MainView.scale(-10), MainView.scale(-10), MainView.scale(-10)));
        GridPane.setColumnSpan(this, 2);

        label = new Label();
        label.textProperty().bind(text);
        label.setLayoutX(MainView.scale(8));
        label.setLayoutY(MainView.scale(-8));
        label.setPadding(new Insets(MainView.scale(0), MainView.scale(7), MainView.scale(0), MainView.scale(5)));
        setActive();
        getChildren().add(label);
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

    public Label getLabel() {
        return label;
    }

}
