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

package io.bisq.gui.main.overlays.windows;

import io.bisq.common.locale.Res;
import io.bisq.core.alert.Alert;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.util.FormBuilder;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class DisplayAlertMessageWindow extends Overlay<DisplayAlertMessageWindow> {
    private static final Logger log = LoggerFactory.getLogger(DisplayAlertMessageWindow.class);
    private Alert alert;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisplayAlertMessageWindow() {
        type = Type.Attention;
    }

    public void show() {
        width = 700;
        // need to set headLine, otherwise the fields will not be created in addHeadLine
        headLine = Res.get("displayAlertMessageWindow.headline");
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        applyStyles();
        display();
    }

    public DisplayAlertMessageWindow alertMessage(Alert alert) {
        this.alert = alert;
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        checkNotNull(alert, "alertMessage must not be null");
        FormBuilder.addMultilineLabel(gridPane, ++rowIndex, alert.getMessage(), 10);
        if (alert.isUpdateInfo()) {
            headLine = Res.get("displayAlertMessageWindow.update.headline");
            headLineLabel.setStyle("-fx-text-fill: -fx-accent;  -fx-font-weight: bold;  -fx-font-size: 22;");
            String url = "https://bisq.network/downloads";
            HyperlinkWithIcon hyperlinkWithIcon = FormBuilder.addLabelHyperlinkWithIcon(gridPane, ++rowIndex,
                    Res.get("displayAlertMessageWindow.update.download"), url, url).second;
            hyperlinkWithIcon.setMaxWidth(550);
        } else {
            headLine = Res.get("displayAlertMessageWindow.headline");
            headLineLabel.setStyle("-fx-text-fill: -bs-error-red;  -fx-font-weight: bold;  -fx-font-size: 22;");
        }
        closeButton = new Button(Res.get("shared.close"));
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        GridPane.setRowIndex(closeButton, ++rowIndex);
        GridPane.setColumnIndex(closeButton, 1);
        gridPane.getChildren().add(closeButton);
        GridPane.setMargin(closeButton, new Insets(10, 0, 0, 0));
    }


}
