/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.overlays.windows;

import io.bitsquare.alert.Alert;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.main.overlays.Overlay;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.gui.util.FormBuilder.addLabelHyperlinkWithIcon;
import static io.bitsquare.gui.util.FormBuilder.addMultilineLabel;

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
        headLine = "Important information!";
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
        addMultilineLabel(gridPane, ++rowIndex, alert.message, 10);
        if (alert.isUpdateInfo) {
            headLine = "Important update information!";
            headLineLabel.setStyle("-fx-text-fill: -fx-accent;  -fx-font-weight: bold;  -fx-font-size: 22;");
            String url = "https://bitsquare.io/downloads";
            HyperlinkWithIcon hyperlinkWithIcon = addLabelHyperlinkWithIcon(gridPane, ++rowIndex,
                    "Download:", url, url).second;
            hyperlinkWithIcon.setMaxWidth(550);
        } else {
            headLine = "Important information!";
            headLineLabel.setStyle("-fx-text-fill: -bs-error-red;  -fx-font-weight: bold;  -fx-font-size: 22;");
        }
        closeButton = new Button("Close");
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });

        GridPane.setRowIndex(closeButton, ++rowIndex);
        GridPane.setColumnIndex(closeButton, 1);
        gridPane.getChildren().add(closeButton);
        GridPane.setMargin(closeButton, new Insets(10, 0, 0, 0));
    }


}
