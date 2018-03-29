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

import bisq.common.UserThread;

import org.controlsfx.control.PopOver;

import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.text.Text;

import javafx.geometry.Insets;

import java.util.concurrent.TimeUnit;

import static bisq.desktop.util.FormBuilder.getIcon;



import de.jensd.fx.glyphs.GlyphIcons;

public class InfoAutoTooltipLabel extends AutoTooltipLabel {

    private Text textIcon;
    private Boolean hidePopover;
    private PopOver infoPopover;

    public InfoAutoTooltipLabel(String text, GlyphIcons icon, ContentDisplay contentDisplay, String info) {
        super(text);

        textIcon = getIcon(icon);
        textIcon.setOpacity(0.4);

        textIcon.setOnMouseEntered(e -> {
            hidePopover = false;
            final Label helpLabel = new Label(info);
            helpLabel.setMaxWidth(300);
            helpLabel.setWrapText(true);
            helpLabel.setPadding(new Insets(10));
            showInfoPopOver(helpLabel);
        });

        textIcon.setOnMouseExited(e -> {
            if (infoPopover != null)
                infoPopover.hide();
            hidePopover = true;
            UserThread.runAfter(() -> {
                if (hidePopover) {
                    infoPopover.hide();
                    hidePopover = false;
                }
            }, 250, TimeUnit.MILLISECONDS);
        });

        setGraphic(textIcon);
        setContentDisplay(contentDisplay);
    }

    private void showInfoPopOver(Node node) {
        node.getStyleClass().add("default-text");

        infoPopover = new PopOver(node);
        if (textIcon.getScene() != null) {
            infoPopover.setDetachable(false);
            infoPopover.setArrowLocation(PopOver.ArrowLocation.LEFT_CENTER);

            infoPopover.show(textIcon, -10);
        }
    }
}
