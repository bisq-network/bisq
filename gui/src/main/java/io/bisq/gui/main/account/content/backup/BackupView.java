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

package io.bisq.gui.main.account.content.backup;

import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.user.Preferences;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.Layout;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

@FxmlView
public class BackupView extends ActivatableView<GridPane, Void> {
    private final File dataDir, logFile;
    private int gridRow = 0;
    private final Stage stage;
    private final Preferences preferences;
    private Button selectBackupDir, backupNow;
    private TextField backUpLocationTextField;
    private Button openDataDirButton, openLogsButton;
    private ChangeListener<Boolean> backUpLocationTextFieldFocusListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BackupView(Stage stage, Preferences preferences, BisqEnvironment environment) {
        super();
        this.stage = stage;
        this.preferences = preferences;
        dataDir = new File(environment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY));
        logFile = new File(Paths.get(dataDir.getPath(), "bisq.log").toString());
    }


    @Override
    public void initialize() {
        FormBuilder.addTitledGroupBg(root, gridRow, 1, Res.get("account.backup.title"));
        Tuple2<Label, InputTextField> tuple = FormBuilder.addLabelInputTextField(root, gridRow, Res.get("account.backup.location"), Layout.FIRST_ROW_DISTANCE);
        backUpLocationTextField = tuple.second;
        String backupDirectory = preferences.getBackupDirectory();
        if (backupDirectory != null)
            backUpLocationTextField.setText(backupDirectory);

        backUpLocationTextFieldFocusListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue)
                applyBackupDirectory(backUpLocationTextField.getText());
        };

        Tuple2<Button, Button> tuple2 = FormBuilder.add2ButtonsAfterGroup(root, ++gridRow,
                Res.get("account.backup.selectLocation"), Res.get("account.backup.backupNow"));
        selectBackupDir = tuple2.first;
        backupNow = tuple2.second;
        updateButtons();

        FormBuilder.addTitledGroupBg(root, ++gridRow, 2, Res.get("account.backup.appDir"), Layout.GROUP_DISTANCE);
        openDataDirButton = FormBuilder.addLabelButton(root, gridRow, Res.getWithCol("account.backup.appDir"),
                Res.get("account.backup.openDirectory"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        openDataDirButton.setDefaultButton(false);
        openLogsButton = FormBuilder.addLabelButton(root, ++gridRow, Res.getWithCol("account.backup.logFile"),
                Res.get("account.backup.openLogFile")).second;
        openLogsButton.setDefaultButton(false);
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
                File dir = directoryChooser.showDialog(stage);
                if (dir != null) {
                    applyBackupDirectory(dir.getAbsolutePath());
                }
            } catch (Throwable t) {
                showWrongPathWarningAndReset(t);
            }

        });
        openDataDirButton.setOnAction(event -> {
            try {
                Utilities.openFile(dataDir);
            } catch (IOException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                showWrongPathWarningAndReset(e);
            }
        });
        openLogsButton.setOnAction(event -> {
            try {
                Utilities.openFile(logFile);
            } catch (IOException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                showWrongPathWarningAndReset(e);
            }
        });

        backupNow.setOnAction(event -> {
            String backupDirectory = preferences.getBackupDirectory();
            if (backupDirectory != null && backupDirectory.length() > 0) {
                try {
                    String dateString = new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date());
                    String destination = Paths.get(backupDirectory, "bisq_backup_" + dateString).toString();
                    FileUtils.copyDirectory(dataDir,
                            new File(destination));
                    new Popup<>().feedback(Res.get("account.backup.success", destination)).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error(e.getMessage());
                    showWrongPathWarningAndReset(e);
                }
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
        new Popup<>().warning(Res.get("account.backup.directoryNotAccessible", error)).show();
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

