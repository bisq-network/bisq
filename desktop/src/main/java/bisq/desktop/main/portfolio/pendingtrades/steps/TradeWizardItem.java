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

package bisq.desktop.main.portfolio.pendingtrades.steps;

import bisq.desktop.util.Colors;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Button;
import javafx.scene.control.Label;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

public class TradeWizardItem extends Button {
    public Class<? extends TradeStepView> getViewClass() {
        return viewClass;
    }

    private final Class<? extends TradeStepView> viewClass;

    public TradeWizardItem(Class<? extends TradeStepView> viewClass, String title) {
        this.viewClass = viewClass;

        setMouseTransparent(true);
        setText(title);
        setPrefHeight(40);
        setPrefWidth(360);
        setAlignment(Pos.CENTER_LEFT);
        setDisabled();
    }

    public void setDisabled() {
        setId("trade-wizard-item-background-disabled");
        Label icon = new Label();
        icon.setPadding(new Insets(-3, 6, 0, 0));
        icon.setTextFill(Colors.LIGHT_GREY);
        AwesomeDude.setIcon(icon, AwesomeIcon.SPINNER);
        setGraphic(icon);
    }

    public void setActive() {
        setId("trade-wizard-item-background-active");
        Label icon = new Label();
        icon.setPadding(new Insets(-3, 6, 0, 0));
        icon.setTextFill(Colors.BLUE);
        AwesomeDude.setIcon(icon, AwesomeIcon.ARROW_RIGHT);
        setGraphic(icon);
    }

    public void setCompleted() {
        setId("trade-wizard-item-background-completed");
        Label icon = new Label();
        icon.setPadding(new Insets(-3, 6, 0, 0));
        icon.setTextFill(Colors.GREEN);
        AwesomeDude.setIcon(icon, AwesomeIcon.OK);
        setGraphic(icon);
    }
}
