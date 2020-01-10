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

package bisq.desktop.main.overlays.windows.downloadupdate;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.alert.Alert;
import bisq.core.locale.Res;

import bisq.common.config.Config;
import bisq.common.util.Utilities;

import com.google.common.base.Joiner;

import com.jfoenix.controls.JFXProgressBar;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addLabel;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class DisplayUpdateDownloadWindow extends Overlay<DisplayUpdateDownloadWindow> {
    private final Alert alert;
    private final Config config;
    private Optional<DownloadTask> downloadTaskOptional;
    private VerifyTask verifyTask;
    private ProgressBar progressBar;
    private BusyAnimation busyAnimation;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisplayUpdateDownloadWindow(Alert alert, Config config) {
        this.alert = alert;
        this.config = config;
        this.type = Type.Attention;
    }

    public void show() {
        width = 968;
        // need to set headLine, otherwise the fields will not be created in addHeadLine
        createGridPane();
        information(""); // to set regular information styling
        headLine = Res.get("displayUpdateDownloadWindow.headline");
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        checkNotNull(alert, "alertMessage must not be null");
        addMultilineLabel(gridPane, ++rowIndex, alert.getMessage(), 10);

        Separator separator = new Separator();
        separator.setMouseTransparent(true);
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.getStyleClass().add("separator");
        GridPane.setHalignment(separator, HPos.CENTER);
        GridPane.setRowIndex(separator, ++rowIndex);
        GridPane.setColumnSpan(separator, 2);
        GridPane.setMargin(separator, new Insets(20, 0, 20, 0));
        gridPane.getChildren().add(separator);


        Button downloadButton = new AutoTooltipButton(Res.get("displayUpdateDownloadWindow.button.label"));
        downloadButton.getStyleClass().add("action-button");
        downloadButton.setDefaultButton(true);

        busyAnimation = new BusyAnimation(false);

        Label statusLabel = new AutoTooltipLabel();
        statusLabel.managedProperty().bind(statusLabel.visibleProperty());

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(downloadButton, busyAnimation, statusLabel);

        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 0);
        GridPane.setColumnSpan(hBox, 2);
        gridPane.getChildren().add(hBox);

        Label downloadingFileLabel = addLabel(gridPane, ++rowIndex,
                Res.get("displayUpdateDownloadWindow.downloadingFile", ""));
        downloadingFileLabel.setOpacity(0.2);
        GridPane.setHalignment(downloadingFileLabel, HPos.LEFT);

        progressBar = new JFXProgressBar(0L);
        progressBar.setMaxHeight(4);
        progressBar.managedProperty().bind(progressBar.visibleProperty());

        GridPane.setRowIndex(progressBar, ++rowIndex);
        GridPane.setHalignment(progressBar, HPos.LEFT);
        GridPane.setFillWidth(progressBar, true);
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
        separator2.getStyleClass().add("separator");
        GridPane.setHalignment(separator2, HPos.CENTER);
        GridPane.setRowIndex(separator2, ++rowIndex);
        GridPane.setColumnSpan(separator2, 2);
        GridPane.setMargin(separator2, new Insets(20, 0, 20, 0));
        gridPane.getChildren().add(separator2);

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
                        statusLabel.setText(Res.get("displayUpdateDownloadWindow.status.verifying"));

                        List<BisqInstaller.FileDescriptor> downloadResults = downloadTask.getValue();
                        Optional<BisqInstaller.FileDescriptor> downloadFailed = downloadResults.stream()
                                .filter(fileDescriptor -> !BisqInstaller.DownloadStatusEnum.OK.equals(fileDescriptor.getDownloadStatus()))
                                .findFirst();
                        downloadedFilesLabel.getStyleClass().removeAll("error-text", "success-text");
                        if (downloadResults == null || downloadResults.isEmpty() || downloadFailed.isPresent()) {
                            showErrorMessage(downloadButton, statusLabel, downloadFailedString);
                            downloadedFilesLabel.getStyleClass().add("error-text");
                        } else {
                            log.debug("Download completed successfully.");
                            downloadedFilesLabel.getStyleClass().add("success-text");

                            downloadTask.getFileDescriptors().stream()
                                    .filter(fileDescriptor -> fileDescriptor.getType() == BisqInstaller.DownloadType.JAR_HASH)
                                    .findFirst()
                                    .ifPresent(this::copyJarHashToDataDir);

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

                                List<BisqInstaller.VerifyDescriptor> verifyResults = verifyTask.getValue();
                                // check that there are no failed verifications
                                Optional<BisqInstaller.VerifyDescriptor> verifyFailed = verifyResults.stream()
                                        .filter(verifyDescriptor -> !BisqInstaller.VerifyStatusEnum.OK.equals(verifyDescriptor.getVerifyStatusEnum())).findFirst();
                                if (verifyResults == null || verifyResults.isEmpty() || verifyFailed.isPresent()) {
                                    showErrorMessage(downloadButton, statusLabel, Res.get("displayUpdateDownloadWindow.verify.failed"));
                                } else {
                                    verifiedSigLabel.getStyleClass().add("success-text");
                                    new Popup().feedback(Res.get("displayUpdateDownloadWindow.success"))
                                            .actionButtonText(Res.get("displayUpdateDownloadWindow.download.openDir"))
                                            .onAction(() -> {
                                                try {
                                                    Utilities.openFile(new File(Utilities.getDownloadOfHomeDir()));
                                                    doClose();
                                                } catch (IOException e2) {
                                                    log.error(e2.getMessage());
                                                    e2.printStackTrace();
                                                }
                                            })
                                            .onClose(this::doClose)
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

    private void copyJarHashToDataDir(BisqInstaller.FileDescriptor fileDescriptor) {
        StringBuilder sb = new StringBuilder();
        final File sourceFile = fileDescriptor.getSaveFile();
        try (Scanner scanner = new Scanner(new FileReader(sourceFile))) {
            while (scanner.hasNext()) {
                sb.append(scanner.next());
            }
            scanner.close();
            final String hashOfJar = sb.toString();

            Path path = Paths.get(config.appDataDir.getPath(), fileDescriptor.getFileName());
            final String target = path.toString();
            try (PrintWriter writer = new PrintWriter(target, "UTF-8")) {
                writer.println(hashOfJar);
                writer.close();
                log.info("Copied hash of jar from {} to {}", sourceFile.getAbsolutePath(), target);
            } catch (Exception e) {
                log.error(e.toString());
                e.printStackTrace();
            }
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
        }
    }

    @Override
    protected void addButtons() {
        closeButton = new AutoTooltipButton(Res.get("displayUpdateDownloadWindow.button.ignoreDownload"));
        closeButton.setOnAction(event -> doClose());
        actionButton = new AutoTooltipButton(Res.get("displayUpdateDownloadWindow.button.downloadLater"));
        actionButton.setDefaultButton(false);
        actionButton.setOnAction(event -> {
            cleanup();
            hide();
            actionHandlerOptional.ifPresent(Runnable::run);
        });

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(closeButton, actionButton);

        GridPane.setHalignment(hBox, HPos.LEFT);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnSpan(hBox, 2);
        GridPane.setMargin(hBox, new Insets(buttonDistance, 0, 0, 0));
        gridPane.getChildren().add(hBox);
    }

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.ENTER) {
                    e.consume();
                    cleanup();
                    hide();
                    actionHandlerOptional.ifPresent(Runnable::run);
                }
            });
        }
    }

    @Override
    protected void doClose() {
        super.doClose();

        cleanup();
        stopAnimations();

        hide();
        closeHandlerOptional.ifPresent(Runnable::run);
    }

    @Override
    protected void cleanup() {
        super.cleanup();

        if (verifyTask != null && verifyTask.isRunning())
            verifyTask.cancel();
        if (downloadTaskOptional != null && downloadTaskOptional.isPresent() && downloadTaskOptional.get().isRunning())
            downloadTaskOptional.get().cancel();
    }

    private void showErrorMessage(Button downloadButton, Label statusLabel, String errorMsg) {
        statusLabel.setText("");
        stopAnimations();
        downloadButton.setDisable(false);
        new Popup()
                .headLine(Res.get("displayUpdateDownloadWindow.download.failed.headline"))
                .feedback(errorMsg)
                .onClose(this::doClose)
                .show();
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

