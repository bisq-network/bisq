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

package bisq.desktop.main.account.content.backup;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import bisq.common.config.Config;
import bisq.common.file.FileUtil;
import bisq.common.persistence.PersistenceManager;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import javax.inject.Inject;

import javafx.stage.DirectoryChooser;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import java.text.SimpleDateFormat;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.Date;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.add2Buttons;
import static bisq.desktop.util.FormBuilder.add2ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BackupView extends ActivatableView<GridPane, Void> {
    private final File dataDir, logFile;
    private int gridRow = 0;
    private final Preferences preferences;
    private Button selectBackupDir, backupNow;
    private TextField backUpLocationTextField;
    private Button openDataDirButton, openLogsButton;
    private ChangeListener<Boolean> backUpLocationTextFieldFocusListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BackupView(Preferences preferences, Config config) {
        super();
        this.preferences = preferences;
        dataDir = new File(config.appDataDir.getPath());
        logFile = new File(Paths.get(dataDir.getPath(), "bisq.log").toString());
    }


    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 2, Res.get("account.backup.title"));
        backUpLocationTextField = addInputTextField(root, gridRow, Res.get("account.backup.location"), Layout.FIRST_ROW_DISTANCE);
        String backupDirectory = preferences.getBackupDirectory();
        if (backupDirectory != null)
            backUpLocationTextField.setText(backupDirectory);

        backUpLocationTextFieldFocusListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue)
                applyBackupDirectory(backUpLocationTextField.getText());
        };

        Tuple2<Button, Button> tuple2 = add2ButtonsAfterGroup(root, ++gridRow,
                Res.get("account.backup.selectLocation"), Res.get("account.backup.backupNow"));
        selectBackupDir = tuple2.first;
        backupNow = tuple2.second;
        updateButtons();

        addTitledGroupBg(root, ++gridRow, 2, Res.get("account.backup.appDir"), Layout.GROUP_DISTANCE);

        final Tuple2<Button, Button> applicationDataDirTuple2 = add2Buttons(root, gridRow, Res.get("account.backup.openDirectory"),
                Res.get("account.backup.openLogFile"), Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE, false);

        openDataDirButton = applicationDataDirTuple2.first;
        openLogsButton = applicationDataDirTuple2.second;
    }

    @Override
    protected void activate() {
        backUpLocationTextField.focusedProperty().addListener(backUpLocationTextFieldFocusListener);
        selectBackupDir.setOnAction(e -> {
            String path = preferences.getDirectoryChooserPath();
            if (!Utilities.isDirectory(path)) {
                path = Utilities.getSystemHomeDirectory();
                backUpLocationTextField.setText(path);
            }
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setInitialDirectory(new File(path));
            directoryChooser.setTitle(Res.get("account.backup.selectLocation"));
            try {
                File dir = directoryChooser.showDialog(root.getScene().getWindow());
                if (dir != null) {
                    applyBackupDirectory(dir.getAbsolutePath());
                }
            } catch (Throwable t) {
                showWrongPathWarningAndReset(t);
            }

        });
        openFileOrShowWarning(openDataDirButton, dataDir);
        openFileOrShowWarning(openLogsButton, logFile);

        backupNow.setOnAction(event -> {
            String backupDirectory = preferences.getBackupDirectory();
            if (backupDirectory != null && backupDirectory.length() > 0) {  // We need to flush data to disk
                PersistenceManager.flushAllDataToDiskAtBackup(() -> {
                    try {
                        String dateString = new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date());
                        String destination = Paths.get(backupDirectory, "bisq_backup_" + dateString).toString();
                        FileUtil.copyDirectory(dataDir, new File(destination));
                        new Popup().feedback(Res.get("account.backup.success", destination)).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                        log.error(e.getMessage());
                        showWrongPathWarningAndReset(e);
                    }
                });
            }
        });
    }

    private void openFileOrShowWarning(Button button, File dataDir) {
        button.setOnAction(event -> {
            try {
                Utilities.openFile(dataDir);
            } catch (IOException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                showWrongPathWarningAndReset(e);
            }
        });
    }

    @Override
    protected void deactivate() {
        backUpLocationTextField.focusedProperty().removeListener(backUpLocationTextFieldFocusListener);
        selectBackupDir.setOnAction(null);
        openDataDirButton.setOnAction(null);
        openLogsButton.setOnAction(null);
        backupNow.setOnAction(null);
    }

    private void updateButtons() {
        boolean noBackupSet = backUpLocationTextField.getText() == null || backUpLocationTextField.getText().length() == 0;
        selectBackupDir.setDefaultButton(noBackupSet);
        backupNow.setDefaultButton(!noBackupSet);
        backupNow.setDisable(noBackupSet);
    }

    private void showWrongPathWarningAndReset(@Nullable Throwable t) {
        String error = t != null ? Res.get("shared.errorMessageInline", t.getMessage()) : "";
        new Popup().warning(Res.get("account.backup.directoryNotAccessible", error)).show();
        applyBackupDirectory(Utilities.getSystemHomeDirectory());
    }

    private void applyBackupDirectory(String path) {
        if (isPathValid(path)) {
            preferences.setDirectoryChooserPath(path);
            backUpLocationTextField.setText(path);
            preferences.setBackupDirectory(path);
            updateButtons();
        } else {
            showWrongPathWarningAndReset(null);
        }
    }

    private boolean isPathValid(String path) {
        return path == null || path.isEmpty() || Utilities.isDirectory(path);
    }
}

