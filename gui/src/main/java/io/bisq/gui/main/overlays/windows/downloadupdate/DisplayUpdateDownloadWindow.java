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

package io.bisq.gui.main.overlays.windows.downloadupdate;

import io.bisq.common.locale.Res;
import io.bisq.common.util.Utilities;
import io.bisq.core.alert.Alert;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.main.overlays.windows.downloadupdate.BisqInstaller.VerifyDescriptor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.gui.util.FormBuilder.addButton;
import static io.bisq.gui.util.FormBuilder.addMultilineLabel;

@Slf4j
public class DisplayUpdateDownloadWindow extends Overlay<DisplayUpdateDownloadWindow> {
    private Alert alert;
    private Optional<DownloadTask> downloadTask;
    private VerifyTask verifyTask;
    private Label downloadMessageLabel;
    private Label verifyMessageLabel;
    private ProgressBar progressBar;
    private Button openDataDirButton;
    private final String DOWNLOAD_FAILED = Res.get("displayUpdateDownloadWindow.download.failed");


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisplayUpdateDownloadWindow(Alert alert) {
        this.type = Type.Attention;
        this.alert = alert;
    }

    public void show() {
        width = 700;
        // need to set headLine, otherwise the fields will not be created in addHeadLine
        headLine = Res.get("displayUpdateDownloadWindow.headline");
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        applyStyles();
        display();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        checkNotNull(alert, "alertMessage must not be null");
        downloadMessageLabel = addMultilineLabel(gridPane, ++rowIndex, alert.getMessage(), 10);
        verifyMessageLabel = addMultilineLabel(gridPane, ++rowIndex, "", 10);
        verifyMessageLabel.setVisible(false);
        headLine = "Important update information!";
        headLineLabel.setStyle("-fx-text-fill: -fx-accent;  -fx-font-weight: bold;  -fx-font-size: 22;");

        progressBar = new ProgressBar(0L);
        progressBar.setVisible(false);
        GridPane.setRowIndex(progressBar, ++rowIndex);
        GridPane.setColumnIndex(progressBar, 1);
        gridPane.getChildren().add(progressBar);

        Button downloadButton = addButton(gridPane, ++rowIndex, "Download now");

        BisqInstaller installer = new BisqInstaller();

        // unknown OS? Show failure message
        if (!installer.osCheck()) {
            downloadButton.setDisable(true);
            downloadMessageLabel.setText(Res.get("displayUpdateDownloadWindow.installer.failed"));
        }

        downloadButton.setOnAction(e -> {
            downloadButton.setDisable(true);
            progressBar.setVisible(true);

            // download installer
            downloadTask = installer.download(alert.getVersion(), progressBar);
            downloadMessageLabel.textProperty().bind(downloadTask.get().messageProperty());

            if (!downloadTask.isPresent()) {
                downloadMessageLabel.setText(DOWNLOAD_FAILED);
                return;
            }

            downloadTask.get().setOnSucceeded(evt -> {
                List<BisqInstaller.FileDescriptor> downloadResults = downloadTask.get().getValue();
                downloadMessageLabel.textProperty().unbind();
                Optional<BisqInstaller.FileDescriptor> downloadFailed = downloadResults.stream()
                        .filter(fileDescriptor -> !BisqInstaller.DownloadStatusEnum.OK.equals(fileDescriptor.getDownloadStatus())).findFirst();
                if (downloadResults == null || downloadFailed.isPresent()) {
                    downloadMessageLabel.setText(DOWNLOAD_FAILED);
                } else {
                    downloadMessageLabel.setText(Res.get("displayUpdateDownloadWindow.download.succes"));
                    progressBar.setVisible(false);
                    log.debug("Download completed successfully.");
                    verifyTask = installer.verify(downloadResults);
                    verifyMessageLabel.textProperty().bind(verifyTask.messageProperty());
                    verifyMessageLabel.setVisible(true);

                    verifyTask.setOnSucceeded(event -> {
                        List<VerifyDescriptor> verifyResults = verifyTask.getValue();
                        verifyMessageLabel.textProperty().unbind();
                        Optional<VerifyDescriptor> verifyFailed = verifyResults.stream()
                                .filter(verifyDescriptor -> !BisqInstaller.VerifyStatusEnum.OK.equals(verifyDescriptor.getVerifyStatusEnum())).findFirst();
                        if (verifyResults == null || verifyResults.isEmpty() || verifyFailed.isPresent()) {
                            verifyMessageLabel.setText(Res.get("displayUpdateDownloadWindow.verify.failed"));
                        } else {
                            verifyMessageLabel.setText(Res.get("displayUpdateDownloadWindow.verify.success", verifyResults.size()));
                            downloadButton.setVisible(false);
                            openDataDirButton.setVisible(true);
                            log.info("Download & verification succeeded.");
                        }
                    });
                }
            });
        });

        openDataDirButton = addButton(gridPane, rowIndex,
                Res.get("displayUpdateDownloadWindow.download.opendir"));
        openDataDirButton.setVisible(false);
        openDataDirButton.setOnAction(event -> {
            try {
                Utilities.openFile(new File(Utilities.getTmpDir()));
            } catch (IOException r) {
                r.printStackTrace();
                log.error(r.getMessage());
            }
        });

        closeButton = addButton(gridPane, ++rowIndex,
                Res.get("shared.close"));
        closeButton.setOnAction(e -> {
            hide();
            if (verifyTask != null && verifyTask.isRunning())
                verifyTask.cancel();
            if (downloadTask != null && downloadTask.isPresent() && downloadTask.get().isRunning())
                downloadTask.get().cancel();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });
    }
}

