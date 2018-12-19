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

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.controlsfx.control.PopOver;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;

import java.util.concurrent.TimeUnit;

public class AutoTooltipTableColumn<S, T> extends TableColumn<S, T> {

    private Label helpIcon;
    private Boolean hidePopover;
    private PopOver infoPopover;


    public AutoTooltipTableColumn(String text) {
        super();

        setTitle(text);
    }

    public AutoTooltipTableColumn(String text, String help) {

        setTitleWithHelpText(text, help);
    }

    public void setTitle(String title) {
        setGraphic(new AutoTooltipLabel(title));
    }

    public void setTitleWithHelpText(String title, String help) {

        final AutoTooltipLabel label = new AutoTooltipLabel(title);

        helpIcon = new Label();
        AwesomeDude.setIcon(helpIcon, AwesomeIcon.QUESTION_SIGN, "1em");
        helpIcon.setOpacity(0.4);
        helpIcon.setOnMouseEntered(e -> {
            hidePopover = false;
            final Label helpLabel = new Label(help);
            helpLabel.setMaxWidth(300);
            helpLabel.setWrapText(true);
            helpLabel.setPadding(new Insets(10));
            showInfoPopOver(helpLabel);
        });
        helpIcon.setOnMouseExited(e -> {
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

        final HBox hBox = new HBox(label, helpIcon);
        hBox.setStyle("-fx-alignment: center-left");
        hBox.setSpacing(4);
        setGraphic(hBox);
    }

    private void showInfoPopOver(Node node) {
        node.getStyleClass().add("default-text");

        infoPopover = new PopOver(node);
        if (helpIcon.getScene() != null) {
            infoPopover.setDetachable(false);
            infoPopover.setArrowLocation(PopOver.ArrowLocation.LEFT_CENTER);

            infoPopover.show(helpIcon, -10);
        }
    }
}
