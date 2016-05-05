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

package io.bitsquare.gui.main.account.content.backup;

import io.bitsquare.app.BitsquareEnvironment;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.user.Preferences;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class BackupView extends ActivatableView<GridPane, Void> {


    private final File dataDir;
    private int gridRow = 0;
    private final Stage stage;
    private final Preferences preferences;
    private Button selectBackupDir, backupNow;
    private TextField backUpLocationTextField;
    private Button openDataDir;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BackupView(Stage stage, Preferences preferences, BitsquareEnvironment environment) {
        super();
        this.stage = stage;
        this.preferences = preferences;
        dataDir = new File(environment.getProperty(BitsquareEnvironment.APP_DATA_DIR_KEY));
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 2, "Backup wallet and data directory");
        Tuple3<Label, TextField, Button> tuple = addLabelTextFieldButton(root, gridRow, "Backup location:", "Select backup location", Layout.FIRST_ROW_DISTANCE);

        backUpLocationTextField = tuple.second;
        if (preferences.getBackupDirectory() != null)
            backUpLocationTextField.setText(preferences.getBackupDirectory());
        selectBackupDir = tuple.third;
        openDataDir = addLabelButton(root, ++gridRow, "Application data directory:", "Open directory", 0).second;
        openDataDir.setDefaultButton(false);
        backupNow = addButtonAfterGroup(root, ++gridRow, "Backup now (backup is not encrypted!)");
        backupNow.setDisable(preferences.getBackupDirectory() == null || preferences.getBackupDirectory().length() == 0);
        backupNow.setDefaultButton(preferences.getBackupDirectory() != null);
    }

    @Override
    protected void activate() {
        selectBackupDir.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            directoryChooser.setTitle("Select backup location");
            File dir = directoryChooser.showDialog(stage);
            if (dir != null) {
                String backupDirectory = dir.getAbsolutePath();
                backUpLocationTextField.setText(backupDirectory);
                preferences.setBackupDirectory(backupDirectory);
                backupNow.setDisable(false);
                backupNow.setDefaultButton(true);
                selectBackupDir.setDefaultButton(false);
            }
        });
        openDataDir.setOnAction(e -> {
            try {
                Utilities.openDirectory(dataDir);
            } catch (IOException e1) {
                log.error(e1.getMessage());
                new Popup().warning("Cannot open directory.\nError =" + e1.getMessage()).show();
            }
        });
        backupNow.setOnAction(e -> {
            String backupDirectory = preferences.getBackupDirectory();
            if (backupDirectory.length() > 0) {
                try {
                    String dateString = new SimpleDateFormat("YYYY-MM-dd-HHmmss").format(new Date());
                    String destination = Paths.get(backupDirectory, "bitsquare_backup_" + dateString).toString();
                    FileUtils.copyDirectory(dataDir,
                            new File(destination));
                    new Popup().feedback("Backup successfully saved at:\n" + destination).show();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    log.error(e1.getMessage());
                    new Popup().error("Backup could not be saved.\nError message: " + e1.getMessage()).show();
                }
            }
        });
    }

    @Override
    protected void deactivate() {
        selectBackupDir.setOnAction(null);
    }


}

