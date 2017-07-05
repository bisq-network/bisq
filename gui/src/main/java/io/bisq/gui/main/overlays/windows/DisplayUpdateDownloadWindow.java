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

package io.bisq.gui.main.overlays.windows;

import io.bisq.common.util.DownloadTask;
import io.bisq.common.util.DownloadType;
import io.bisq.common.util.Utilities;
import io.bisq.core.alert.Alert;
import io.bisq.gui.main.overlays.Overlay;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.gui.util.FormBuilder.addButton;
import static io.bisq.gui.util.FormBuilder.addMultilineLabel;

// TODO: For future use sigFile and key download calls have to loop through keyIDs and verifySignature needs to be called for all sigs/keys

public class DisplayUpdateDownloadWindow extends Overlay<DisplayUpdateDownloadWindow> {
    private static final String[] keyIDs = {"F379A1C6"};
    private static final Logger log = LoggerFactory.getLogger(DisplayUpdateDownloadWindow.class);
    private Alert alert;
    private DownloadTask installerTask, keyTasks[], sigTasks[];
    private boolean downloadSuccessInstaller, downloadSuccessKey[], downloadSuccessSignature[];
    private File installerFile, pubKeyFile[], sigFile[];
    private Label messageLabel;
    private ProgressIndicator indicator;
    private final String DOWNLOAD_FAILED = "Download failed. Please download and verify the correct file yourself from https://bitsquare.io/downloads/";


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
        headLine = "Update available!";
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
        messageLabel = addMultilineLabel(gridPane, ++rowIndex, alert.getMessage(), 10);
        headLine = "Important update information!";
        headLineLabel.setStyle("-fx-text-fill: -fx-accent;  -fx-font-weight: bold;  -fx-font-size: 22;");

        /* DOCUMENTATION:
                FormBuilder.addTitledGroupBg(root, ++gridRow, 2, Res.get("account.backup.appDir"), Layout.GROUP_DISTANCE);
                openDataDirButton = FormBuilder.addLabelButton(root, gridRow, Res.getWithCol("account.backup.appDir"),
                        Res.get("account.backup.openDirectory"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
                openDataDirButton.setDefaultButton(false);
        */

        indicator = new ProgressIndicator(0L);
        indicator.setVisible(false);
        GridPane.setRowIndex(indicator, ++rowIndex);
        GridPane.setColumnIndex(indicator, 1);
        gridPane.getChildren().add(indicator);

        Button downloadButton = addButton(gridPane, ++rowIndex, "Download now");

        // TODO How do we get the right URL for the download? (check for other platforms)

        String url = "https://github.com/bitsquare/bitsquare/releases/download/" + "v" + alert.getVersion() + "/";
        Optional<String> fileNameOptional = getFileName(downloadButton);
        String fileName = fileNameOptional.orElseGet(() -> "");
        if(!fileNameOptional.isPresent()) {
                downloadButton.setDisable(true);
                messageLabel.setText("Unable to determine the correct installer. Please manually download and verify " +
                        "the correct file from https://bitsquare.io/downloads");
        }

        downloadButton.setOnAction(e -> {
            indicator.setVisible(true);
            downloadButton.setDisable(true);

            // download installer
            downloadSuccessInstaller = false;
            try {
                installerTask = Utilities.downloadFile(url.concat(fileName), null, indicator, DownloadType.INST, (byte) 0);
            } catch (IOException exception) {
                messageLabel.setText(DOWNLOAD_FAILED);
                return;
            }
            installerTask.setOnSucceeded(evt -> {
                installerFile = installerTask.getValue();
                if (installerFile == null) {
                    messageLabel.setText(DOWNLOAD_FAILED);
                } else {
                    downloadSuccessInstaller = true;
                }

                System.out.println("Completed installer");
                updateStatusAndFileReferences(installerTask);
                verifySignature();
            });

            // download key file
            keyTasks = new DownloadTask[keyIDs.length];
            pubKeyFile = new File[keyIDs.length];
            downloadSuccessKey = new boolean[keyIDs.length];
            byte i = 0;
            try {
                for (String keyID : keyIDs) {
                    keyTasks[i] = Utilities.downloadFile(url.concat(keyID + ".asc"), null, null, DownloadType.KEY, i);
                    i++;
                }
            } catch (IOException exception) {
                messageLabel.setText(DOWNLOAD_FAILED);
                return;
            }

            for (DownloadTask task : keyTasks) {
//                keyTasks[i].setOnSucceeded(new TaskSucceededHandler(keyTasks[i], pubKeyFile[i], downloadSuccessKey[i]));
                task.setOnSucceeded(evt -> {
/*                    file = task.getValue();
                    if (file == null) {
                        messageLabel.setText(DOWNLOAD_FAILED);
                    } else {
                        downloadSuccessKey[j] = true;
                        verifySignature();
                    }
*/
                    System.out.println("Completed key");
                    updateStatusAndFileReferences(task);
                    verifySignature();
                });
            }

//TODO
            // download signature file
            sigTasks = new DownloadTask[keyIDs.length];
            sigFile = new File[keyIDs.length];
            downloadSuccessSignature = new boolean[]{false};
            try {
                sigTasks[0] = Utilities.downloadFile(url.concat(fileName /*+ "-" + keyIDs[0]*/ + ".asc"), null, null, DownloadType.SIG, (byte) 0);
            } catch (IOException exception) {
                messageLabel.setText(DOWNLOAD_FAILED);
                return;
            }
            sigTasks[0].setOnSucceeded(evt -> {
                System.out.println("Completed sig");
                sigFile[0] = sigTasks[0].getValue();
                if (sigFile == null) {
                    messageLabel.setText(DOWNLOAD_FAILED);
                } else {
                    downloadSuccessSignature[0] = true;
                    verifySignature();
                }
            });
        });

        //TODO
        closeButton = new Button("Close");
        closeButton.setOnAction(e -> {
            hide();
            if (installerTask != null && installerTask.isRunning())
                installerTask.cancel();
            if (keyTasks[0] != null && keyTasks[0].isRunning())
                keyTasks[0].cancel();
            if (sigTasks[0] != null && sigTasks[0].isRunning())
                sigTasks[0].cancel();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });

        GridPane.setRowIndex(closeButton, ++rowIndex);
        GridPane.setColumnIndex(closeButton, 1);
        gridPane.getChildren().add(closeButton);
        GridPane.setMargin(closeButton, new Insets(10, 0, 0, 0));
    }

    @NotNull
    private Optional<String> getFileName(Button downloadButton) {
        String fileName = null;
        String prefix = "Bisq-";
        // https://github.com/bitsquare/bitsquare/releases/download/v0.5.1/Bisq-0.5.1.dmg
        if (Utilities.isOSX())
            fileName = prefix + alert.getVersion() + ".dmg";
        else if (Utilities.isWindows())
            fileName = prefix + Utilities.getOSArchitecture() + "bit-" + alert.getVersion() + ".exe";
        else if (Utilities.isLinux())
            fileName = prefix + Utilities.getOSArchitecture() + "bit-" + alert.getVersion() + ".deb";

        return Optional.of(fileName);
    }

    private void updateStatusAndFileReferences(DownloadTask sourceTask) {
        switch (sourceTask.getDownloadType()) {
            case INST:
                installerFile = sourceTask.getValue();
                System.out.println(installerFile.getName());
                if (installerFile != null)
                    downloadSuccessInstaller = true;
                break;
            case KEY:
                pubKeyFile[sourceTask.getIndex()] = sourceTask.getValue();
                System.out.println(sourceTask.getIndex() + "\t" + pubKeyFile[sourceTask.getIndex()].getName());
                if (pubKeyFile != null)
                    downloadSuccessKey[sourceTask.getIndex()] = true;
                break;
            case SIG:
                sigFile[sourceTask.getIndex()] = sourceTask.getValue();
                System.out.println(sourceTask.getIndex() + "\t" + sigFile[sourceTask.getIndex()].getName());
                if (sigFile[sourceTask.getIndex()] != null)
                    downloadSuccessSignature[sourceTask.getIndex()] = true;
                break;
        }
    }

    private void verifySignature() {
        System.out.print("Inst.: " + downloadSuccessInstaller);
        System.out.print("\tKey: " + downloadSuccessKey[0]);
        System.out.println("\tSig.: " + downloadSuccessSignature[0]);
        if (downloadSuccessInstaller && arrayAnd(downloadSuccessKey) && arrayAnd(downloadSuccessSignature)) {
            VerificationResult[] results = new VerificationResult[keyIDs.length];
            for (int i = 0; i < keyIDs.length; i++) {
                try {
                    boolean verified = DownloadTask.verifySignature(pubKeyFile[i], sigFile[i], installerFile);
                    results[i] = verified ? VerificationResult.GOOD : VerificationResult.BAD;
                    if (verified) {
                        messageLabel.setText("The signature turned out to be GOOD. Please install " + installerFile.getName() + " from " + installerFile.getParent());
                        Utilities.openFile(installerFile.getParentFile());
                    } else {
                        indicator.setVisible(false);
                        messageLabel.setText("The downloaded installer file showed a BAD signature. The file may have been manipulated.");
                    }

                } catch (Exception exc) {
                    results[i] = VerificationResult.EXCEPTION;
                    indicator.setVisible(false);
                    messageLabel.setText("Signature check caused an exception. This does not yet mean a bad signature. Please try to verify yourself.");
                    exc.printStackTrace();
                }
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keyIDs.length; i++)
                sb.append(keyIDs[i] + ":\t" + results[i] + "\n");
            System.out.print(sb.toString());
            indicator.setVisible(false);
            messageLabel.setText(sb.toString());
        }
    }

    private boolean arrayAnd(boolean[] array) {
        boolean and = true;
        for (boolean b : array)
            and &= b;
        return and;
    }
}

class TaskSucceededHandler implements EventHandler<WorkerStateEvent> {
    Task<File> task;
    File file;
    Boolean success;

    TaskSucceededHandler(@NotNull Task<File> t, File f, Boolean s) {
        task = t;
        file = f;

    }

    public void handle(WorkerStateEvent evt) {
        file = task.getValue();
        if (file != null)
            success = true;
    }
}

enum VerificationResult {
    GOOD,
    BAD,
    EXCEPTION
}
