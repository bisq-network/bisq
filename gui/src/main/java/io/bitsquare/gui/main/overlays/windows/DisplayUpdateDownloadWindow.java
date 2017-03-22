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
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.main.overlays.Overlay;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.gui.util.FormBuilder.addButton;
import static io.bitsquare.gui.util.FormBuilder.addLabelHyperlinkWithIcon;
import static io.bitsquare.gui.util.FormBuilder.addMultilineLabel;

public class DisplayUpdateDownloadWindow extends Overlay<DisplayUpdateDownloadWindow> {
    private static final Logger log = LoggerFactory.getLogger(DisplayUpdateDownloadWindow.class);
    private Alert alert;
    private Task<File> task;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisplayUpdateDownloadWindow() {
        type = Type.Attention;
    }

    public void show() {
        width = 700;
        // need to set headLine, otherwise the fields will not be created in addHeadLine
        headLine = "Update available!";
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        applyStyles();
        display();
    }

    public DisplayUpdateDownloadWindow alertMessage(Alert alert) {
        this.alert = alert;
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        checkNotNull(alert, "alertMessage must not be null");
        Label messageLabel = addMultilineLabel(gridPane, ++rowIndex, alert.message, 10);
        headLine = "Important update information!";
        headLineLabel.setStyle("-fx-text-fill: -fx-accent;  -fx-font-weight: bold;  -fx-font-size: 22;");



        ProgressIndicator indicator = new ProgressIndicator(0L);
        indicator.setVisible(false);
        GridPane.setRowIndex(indicator, ++rowIndex);
        GridPane.setColumnIndex(indicator, 1);
        gridPane.getChildren().add(indicator);

        Button downloadButton = addButton(gridPane, ++rowIndex, "Download now");

        // TODO How do we get the right URL for the download?
        String url = "https://github.com/bitsquare/bitsquare/releases/download/" + "v" + alert.version + "/" ;
        String fileName;
        if (Utilities.isOSX())
            fileName = "Bitsquare-" + alert.version + ".dmg";
        else if (Utilities.isWindows())
            fileName = "Bitsquare-" + Utilities.getOSArchitecture() + "bit-" + alert.version + ".exe";
        else if (Utilities.isLinux())
            fileName = "Bitsquare-" + Utilities.getOSArchitecture() + "bit-" + alert.version + ".deb";
        else {
            fileName = "";
            downloadButton.setDisable(true);
            messageLabel.setText("Unable to determine the correct installer. Pleaase manually download and verify " +
                    "the correct file from https://bitsquare.io/downloads");
        }

        downloadButton.setOnAction(e -> {
            String source = url.concat(fileName);
            indicator.setVisible(true);
            downloadButton.setDisable(true);
            try {
                System.out.println("Button: " + source);
                task = Utilities.downloadFile(source, null, indicator);
                task.setOnSucceeded(evt -> {
                    if (task.getValue() == null) {
                        messageLabel.setText("Download failed. Please download and verify the correct file yourself from https://bitsquare.io/downloads/");
                        return;
                    }
                    try {
                        Utilities.openDirectory(task.getValue());
                        messageLabel.setText("Successfully downloaded file " + fileName);
                    } catch (IOException exc) {
                        messageLabel.setText("Unable to open download directory " + task.getValue() + " in file browser.");
                        exc.printStackTrace();
                    }
                });
            } catch (IOException exception)  {
                messageLabel.setText("Unable to download files.\n" +
                "Please manually download and verify the file from https://bitsquare.io/downloads");
            }
        });

        closeButton = new Button("Close");
        closeButton.setOnAction(e -> {
            hide();
            if (task != null && task.isRunning())
                task.cancel();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });

        GridPane.setRowIndex(closeButton, ++rowIndex);
        GridPane.setColumnIndex(closeButton, 1);
        gridPane.getChildren().add(closeButton);
        GridPane.setMargin(closeButton, new Insets(10, 0, 0, 0));
    }


}
