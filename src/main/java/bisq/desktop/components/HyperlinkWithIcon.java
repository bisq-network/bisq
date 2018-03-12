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

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;

import javafx.geometry.Insets;

public class HyperlinkWithIcon extends Hyperlink {

    public HyperlinkWithIcon(String text) {
        this(text, AwesomeIcon.INFO_SIGN);
    }

    public HyperlinkWithIcon(String text, AwesomeIcon awesomeIcon) {

        super(text);

        Label icon = new Label();
        AwesomeDude.setIcon(icon, awesomeIcon);
        icon.setMinWidth(20);
        icon.setOpacity(0.7);
        icon.getStyleClass().add("hyperlink");
        icon.setPadding(new Insets(0));

        setGraphic(icon);
        setContentDisplay(ContentDisplay.RIGHT);
        setGraphicTextGap(7.0);

        //TODO: replace workaround of setting the style this way
        tooltipProperty().addListener((observable, oldValue, newValue) -> newValue.setStyle("-fx-text-fill: -bs-black"));
    }

    public void clear() {
        setText("");
        setGraphic(null);
    }
}
