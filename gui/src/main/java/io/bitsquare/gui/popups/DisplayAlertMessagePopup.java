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

package io.bitsquare.gui.popups;

import io.bitsquare.alert.Alert;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.gui.util.FormBuilder.addLabelHyperlinkWithIcon;
import static io.bitsquare.gui.util.FormBuilder.addMultilineLabel;

public class DisplayAlertMessagePopup extends Popup {
    private static final Logger log = LoggerFactory.getLogger(DisplayAlertMessagePopup.class);
    private Label msgLabel;
    private Alert alert;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisplayAlertMessagePopup() {
    }

    public DisplayAlertMessagePopup show() {
        width = 700;
        // need to set headLine, otherwise the fields will not be created in addHeadLine
        headLine = "Important information!";
        createGridPane();
        addHeadLine();
        addContent();
        createPopup();
        return this;
    }

    public DisplayAlertMessagePopup alertMessage(Alert alert) {
        this.alert = alert;
        return this;
    }

    public DisplayAlertMessagePopup onClose(Runnable closeHandler) {
        this.closeHandlerOptional = Optional.of(closeHandler);
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        checkNotNull(alert, "alertMessage must not be null");
        msgLabel = addMultilineLabel(gridPane, ++rowIndex, alert.message, 10);
        if (alert.isUpdateInfo) {
            headLine = "Important update information!";
            headLineLabel.setStyle("-fx-text-fill: -fx-accent;  -fx-font-weight: bold;  -fx-font-size: 22;");
            String url = "https://github.com/bitsquare/bitsquare/releases";
            HyperlinkWithIcon download = addLabelHyperlinkWithIcon(gridPane, ++rowIndex, "Download:", url).second;
            download.setMaxWidth(350);
            download.setOnAction(e -> Utilities.openWebPage(url));
        } else {
            headLine = "Important information!";
            headLineLabel.setStyle("-fx-text-fill: -bs-error-red;  -fx-font-weight: bold;  -fx-font-size: 22;");
        }
        closeButton = new Button("Cancel");
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
