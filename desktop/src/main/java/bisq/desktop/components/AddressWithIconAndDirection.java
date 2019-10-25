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

import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressWithIconAndDirection extends HBox {
    private static final Logger log = LoggerFactory.getLogger(AddressWithIconAndDirection.class);
    private final Hyperlink hyperlink;

    public AddressWithIconAndDirection(String text, String address, boolean received) {
        Label directionIcon = new Label();
        directionIcon.getStyleClass().add("icon");
        directionIcon.getStyleClass().add(received ? "received-funds-icon" : "sent-funds-icon");
        AwesomeDude.setIcon(directionIcon, received ? AwesomeIcon.SIGNIN : AwesomeIcon.SIGNOUT);
        if (received)
            directionIcon.setRotate(180);
        directionIcon.setMouseTransparent(true);

        setAlignment(Pos.CENTER_LEFT);
        Label label = new AutoTooltipLabel(text);
        label.setMouseTransparent(true);
        HBox.setMargin(directionIcon, new Insets(0, 3, 0, 0));
        HBox.setHgrow(label, Priority.ALWAYS);

        hyperlink = new ExternalHyperlink(address);
        HBox.setMargin(hyperlink, new Insets(0));
        HBox.setHgrow(hyperlink, Priority.SOMETIMES);
        // You need to set max width to Double.MAX_VALUE to make HBox.setHgrow working like expected!
        // also pref width needs to be not default (-1)
        hyperlink.setMaxWidth(Double.MAX_VALUE);
        hyperlink.setPrefWidth(0);

        getChildren().addAll(directionIcon, label, hyperlink);
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        hyperlink.setOnAction(handler);
    }

    public void setTooltip(Tooltip tooltip) {
        hyperlink.setTooltip(tooltip);
    }
}
