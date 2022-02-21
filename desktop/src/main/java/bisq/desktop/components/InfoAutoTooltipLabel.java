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

import de.jensd.fx.fontawesome.AwesomeIcon;
import de.jensd.fx.glyphs.GlyphIcons;

import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;

import javafx.geometry.Insets;

import static bisq.desktop.util.FormBuilder.getIcon;

public class InfoAutoTooltipLabel extends AutoTooltipLabel {

    public static final int DEFAULT_WIDTH = 300;
    private Node textIcon;
    private PopOverWrapper popoverWrapper = new PopOverWrapper();
    private ContentDisplay contentDisplay;

    public InfoAutoTooltipLabel(String text, GlyphIcons icon, ContentDisplay contentDisplay, String info) {
        this(text, contentDisplay);

        setIcon(icon);
        positionAndActivateIcon(contentDisplay, info, DEFAULT_WIDTH);
    }

    public InfoAutoTooltipLabel(String text, AwesomeIcon icon, ContentDisplay contentDisplay, String info, double width) {
        super(text);

        setIcon(icon);
        positionAndActivateIcon(contentDisplay, info, width);
    }

    public InfoAutoTooltipLabel(String text, ContentDisplay contentDisplay) {
        super(text);
        this.contentDisplay = contentDisplay;
    }

    public void setIcon(GlyphIcons icon) {
        textIcon = getIcon(icon);
    }

    public void setIcon(GlyphIcons icon, String info) {
        setIcon(icon);
        positionAndActivateIcon(contentDisplay, info, DEFAULT_WIDTH);
    }

    public void setIcon(AwesomeIcon icon) {
        textIcon = getIcon(icon);
    }

    public void hideIcon() {
        textIcon = null;
        setGraphic(textIcon);
    }

    // May be required until https://bugs.openjdk.java.net/browse/JDK-8265835 is fixed.
    public void disableRolloverPopup() {
        textIcon.setOnMouseEntered(null);
        textIcon.setOnMouseExited(null);
    }

    private void positionAndActivateIcon(ContentDisplay contentDisplay, String info, double width) {
        textIcon.setOpacity(0.4);
        textIcon.getStyleClass().add("tooltip-icon");
        textIcon.setOnMouseEntered(e -> popoverWrapper.showPopOver(() -> createInfoPopOver(info, width)));
        textIcon.setOnMouseExited(e -> popoverWrapper.hidePopOver());

        setGraphic(textIcon);
        setContentDisplay(contentDisplay);
    }

    private PopOver createInfoPopOver(String info, double width) {
        Label helpLabel = new Label(info);
        helpLabel.setMaxWidth(width);
        helpLabel.setWrapText(true);
        helpLabel.setPadding(new Insets(10));
        return createInfoPopOver(helpLabel);
    }

    private PopOver createInfoPopOver(Node node) {
        node.getStyleClass().add("default-text");

        PopOver infoPopover = new PopOver(node);
        if (textIcon.getScene() != null) {
            infoPopover.setDetachable(false);
            infoPopover.setArrowLocation(PopOver.ArrowLocation.LEFT_CENTER);

            infoPopover.show(textIcon, -10);
        }
        return infoPopover;
    }
}
