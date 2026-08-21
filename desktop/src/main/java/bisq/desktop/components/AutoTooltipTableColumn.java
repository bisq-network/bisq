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
import bisq.desktop.util.FormBuilder;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;

public class AutoTooltipTableColumn<S, T> extends TableColumn<S, T> {

    private Label helpIcon;
    private PopOverWrapper popoverWrapper = new PopOverWrapper();

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
        helpIcon = FormBuilder.getSmallIcon(AwesomeIcon.QUESTION_SIGN);
        helpIcon.setOpacity(0.4);
        helpIcon.setOnMouseEntered(e -> popoverWrapper.showPopOver(() -> createInfoPopOver(help)));
        helpIcon.setOnMouseExited(e -> popoverWrapper.hidePopOver());

        final AutoTooltipLabel label = new AutoTooltipLabel(title);
        final HBox hBox = new HBox(label, helpIcon);
        hBox.setStyle("-fx-alignment: center-left");
        hBox.setSpacing(4);
        setGraphic(hBox);
    }

    private PopOver createInfoPopOver(String help) {
        Label helpLabel = new Label(help);
        helpLabel.setMaxWidth(300);
        helpLabel.setWrapText(true);
        return createInfoPopOver(helpLabel);
    }

    private PopOver createInfoPopOver(Node node) {
        node.getStyleClass().add("default-text");

        PopOver infoPopover = new PopOver(node);
        if (helpIcon.getScene() != null) {
            infoPopover.setDetachable(false);
            infoPopover.setArrowLocation(PopOver.ArrowLocation.LEFT_CENTER);

            infoPopover.show(helpIcon, -10);
        }
        return infoPopover;
    }
}
