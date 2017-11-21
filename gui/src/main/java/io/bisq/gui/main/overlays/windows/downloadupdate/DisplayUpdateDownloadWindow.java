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

package io.bisq.gui.main.overlays.windows.downloadupdate;

import com.google.common.base.Joiner;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Utilities;
import io.bisq.core.alert.Alert;
import io.bisq.gui.components.BusyAnimation;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.downloadupdate.BisqInstaller.VerifyDescriptor;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.gui.util.FormBuilder.*;

@Slf4j
public class DisplayUpdateDownloadWindow extends Overlay<DisplayUpdateDownloadWindow> {
    private Alert alert;
    private Optional<DownloadTask> downloadTaskOptional;
    private VerifyTask verifyTask;
    private ProgressBar progressBar;
    private BusyAnimation busyAnimation;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisplayUpdateDownloadWindow(Alert alert) {
        this.type = Type.Attention;
        this.alert = alert;
    }

    public void show() {
        width = 900;
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
        headLine = "Important update information!";
        headLineLabel.setStyle("-fx-text-fill: -fx-accent;  -fx-font-weight: bold;  -fx-font-size: 22;");

        checkNotNull(alert, "alertMessage must not be null");
        addMultilineLabel(gridPane, ++rowIndex, alert.getMessage(), 10);

        Separator separator = new Separator();
        separator.setMouseTransparent(true);
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #ccc;");
        GridPane.setHalignment(separator, HPos.CENTER);
        GridPane.setRowIndex(separator, ++rowIndex);
        GridPane.setColumnSpan(separator, 2);
        GridPane.setMargin(separator, new Insets(20, 0, 20, 0));
        gridPane.getChildren().add(separator);


        Button downloadButton = new Button(Res.get("displayUpdateDownloadWindow.button.label"));
        downloadButton.setDefaultButton(true);

        busyAnimation = new BusyAnimation(false);

        Label statusLabel = new Label();
        statusLabel.managedProperty().bind(statusLabel.visibleProperty());

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(downloadButton, busyAnimation, statusLabel);

        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 0);
        GridPane.setColumnSpan(hBox, 2);
        gridPane.getChildren().add(hBox);


        Label downloadingFileLabel = addLabel(gridPane, ++rowIndex, Res.get("displayUpdateDownloadWindow.downloadingFile", ""));
        GridPane.setColumnIndex(downloadingFileLabel, 0);
        downloadingFileLabel.setOpacity(0.2);

        progressBar = new ProgressBar(0L);
        progressBar.setPrefWidth(200);
        progressBar.setMaxHeight(4);
        progressBar.managedProperty().bind(progressBar.visibleProperty());
        progressBar.setVisible(false);

        GridPane.setRowIndex(progressBar, rowIndex);
        GridPane.setColumnIndex(progressBar, 1);
        GridPane.setHalignment(progressBar, HPos.LEFT);
        GridPane.setFillWidth(progressBar, true);
        GridPane.setMargin(progressBar, new Insets(3, 0, 0, 10));
        gridPane.getChildren().add(progressBar);


        final String downloadedFilesLabelTitle = Res.get("displayUpdateDownloadWindow.downloadedFiles");
        Label downloadedFilesLabel = addLabel(gridPane, ++rowIndex, downloadedFilesLabelTitle);
        GridPane.setColumnIndex(downloadedFilesLabel, 0);
        GridPane.setHalignment(downloadedFilesLabel, HPos.LEFT);
        GridPane.setColumnSpan(downloadedFilesLabel, 2);
        downloadedFilesLabel.setOpacity(0.2);


        final String verifiedSigLabelTitle = Res.get("displayUpdateDownloadWindow.verifiedSigs");
        Label verifiedSigLabel = addLabel(gridPane, ++rowIndex, verifiedSigLabelTitle);
        GridPane.setColumnIndex(verifiedSigLabel, 0);
        GridPane.setColumnSpan(verifiedSigLabel, 2);
        GridPane.setHalignment(verifiedSigLabel, HPos.LEFT);
        verifiedSigLabel.setOpacity(0.2);


        Separator separator2 = new Separator();
        separator2.setMouseTransparent(true);
        separator2.setOrientation(Orientation.HORIZONTAL);
        separator2.setStyle("-fx-background: #ccc;");
        GridPane.setHalignment(separator2, HPos.CENTER);
        GridPane.setRowIndex(separator2, ++rowIndex);
        GridPane.setColumnSpan(separator2, 2);
        GridPane.setMargin(separator2, new Insets(20, 0, 20, 0));
        gridPane.getChildren().add(separator2);


        closeButton = addButton(gridPane, ++rowIndex, Res.get("shared.close"));
        closeButton.setDefaultButton(false);
        GridPane.setColumnIndex(closeButton, 0);
        GridPane.setHalignment(closeButton, HPos.LEFT);
        GridPane.setColumnSpan(closeButton, 2);
        closeButton.setOnAction(e -> {
            if (verifyTask != null && verifyTask.isRunning())
                verifyTask.cancel();
            if (downloadTaskOptional != null && downloadTaskOptional.isPresent() && downloadTaskOptional.get().isRunning())
                downloadTaskOptional.get().cancel();

            stopAnimations();

            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });


        BisqInstaller installer = new BisqInstaller();
        String downloadFailedString = Res.get("displayUpdateDownloadWindow.download.failed");
        downloadButton.setOnAction(e -> {
            if (installer.isSupportedOS()) {
                List<String> downloadedFiles = new ArrayList<>();
                List<String> verifiedSigs = new ArrayList<>();
                downloadButton.setDisable(true);
                progressBar.setVisible(true);
                downloadedFilesLabel.setOpacity(1);
                downloadingFileLabel.setOpacity(1);
                busyAnimation.play();
                statusLabel.setText(Res.get("displayUpdateDownloadWindow.status.downloading"));

                // download installer
                downloadTaskOptional = installer.download(alert.getVersion());
                if (downloadTaskOptional.isPresent()) {
                    final DownloadTask downloadTask = downloadTaskOptional.get();
                    final ChangeListener<String> downloadedFilesListener = (observable, oldValue, newValue) -> {
                        if (!newValue.endsWith("-local")) {
                            downloadingFileLabel.setText(Res.get("displayUpdateDownloadWindow.downloadingFile", newValue));
                            if (downloadedFiles.size() == 1)
                                downloadedFilesLabel.setStyle("-fx-text-fill: -bs-green;");
                            downloadedFilesLabel.setText(downloadedFilesLabelTitle + " " + Joiner.on(", ").join(downloadedFiles));
                            downloadedFiles.add(newValue);
                        }
                    };
                    downloadTask.messageProperty().addListener(downloadedFilesListener);

                    progressBar.progressProperty().unbind();
                    progressBar.progressProperty().bind(downloadTask.progressProperty());
                    downloadTask.setOnSucceeded(workerStateEvent -> {
                        downloadedFilesLabel.setText(downloadedFilesLabelTitle + " " + Joiner.on(", ").join(downloadedFiles));
                        downloadTask.messageProperty().removeListener(downloadedFilesListener);
                        progressBar.setVisible(false);
                        downloadingFileLabel.setText("");
                        downloadingFileLabel.setOpacity(0.2);
                        //-bs-green
                        statusLabel.setText(Res.get("displayUpdateDownloadWindow.status.verifying"));

                        List<BisqInstaller.FileDescriptor> downloadResults = downloadTask.getValue();
                        Optional<BisqInstaller.FileDescriptor> downloadFailed = downloadResults.stream()
                                .filter(fileDescriptor -> !BisqInstaller.DownloadStatusEnum.OK.equals(fileDescriptor.getDownloadStatus())).findFirst();
                        if (downloadResults == null || downloadResults.isEmpty() || downloadFailed.isPresent()) {
                            showErrorMessage(downloadButton, statusLabel, downloadFailedString);
                        } else {
                            log.debug("Download completed successfully.");

                            verifyTask = installer.verify(downloadResults);
                            verifiedSigLabel.setOpacity(1);

                            final ChangeListener<String> verifiedSigLabelListener = (observable, oldValue, newValue) -> {
                                verifiedSigs.add(newValue);
                                verifiedSigLabel.setText(verifiedSigLabelTitle + " " + Joiner.on(", ").join(verifiedSigs));
                            };
                            verifyTask.messageProperty().addListener(verifiedSigLabelListener);

                            verifyTask.setOnSucceeded(event -> {
                                verifyTask.messageProperty().removeListener(verifiedSigLabelListener);
                                statusLabel.setText("");
                                stopAnimations();

                                List<VerifyDescriptor> verifyResults = verifyTask.getValue();
                                // check that there are no failed verifications
                                Optional<VerifyDescriptor> verifyFailed = verifyResults.stream()
                                        .filter(verifyDescriptor -> !BisqInstaller.VerifyStatusEnum.OK.equals(verifyDescriptor.getVerifyStatusEnum())).findFirst();
                                if (verifyResults == null || verifyResults.isEmpty() || verifyFailed.isPresent()) {
                                    showErrorMessage(downloadButton, statusLabel, Res.get("displayUpdateDownloadWindow.verify.failed"));
                                } else {
                                    verifiedSigLabel.setStyle("-fx-text-fill: -bs-green;");
                                    new Popup<>().feedback(Res.get("displayUpdateDownloadWindow.success"))
                                            .actionButtonText(Res.get("displayUpdateDownloadWindow.download.openDir"))
                                            .onAction(() -> {
                                                try {
                                                    Utilities.openFile(new File(Utilities.getDownloadOfHomeDir()));
                                                } catch (IOException e2) {
                                                    log.error(e2.getMessage());
                                                    e2.printStackTrace();
                                                }
                                            })
                                            .show();
                                    log.info("Download & verification succeeded.");
                                }
                            });
                        }
                    });
                } else {
                    showErrorMessage(downloadButton, statusLabel, downloadFailedString);
                }
            } else {
                showErrorMessage(downloadButton, statusLabel, (Res.get("displayUpdateDownloadWindow.installer.failed")));
            }
        });
    }

    private void showErrorMessage(Button downloadButton, Label statusLabel, String errorMsg) {
        statusLabel.setText("");
        stopAnimations();
        downloadButton.setDisable(false);
        new Popup<>().warning(errorMsg).show();
    }

    private void stopAnimations() {
        if (progressBar != null) {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
        }
        if (busyAnimation != null)
            busyAnimation.stop();
    }
}

