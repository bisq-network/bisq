/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */


/**
 * This file is part of bisq.
 * <p/>
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 * <p/>
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 * <p/>
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.overlays.windows;

import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import io.bisq.core.user.Preferences;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.network.p2p.network.DefaultPluggableTransports;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static io.bisq.gui.util.FormBuilder.*;

@Slf4j
public class TorNetworkSettingsWindow extends Overlay<TorNetworkSettingsWindow> {
    private final Preferences preferences;
    private RadioButton noBridgesRadioButton, providedBridgesRadioButton, customBridgesRadioButton;
    private Label enterBridgeLabel;
    private ComboBox<String> transportTypeComboBox;
    private TextArea bridgeEntriesTextArea;
    private Label transportTypeLabel;

    public TorNetworkSettingsWindow(Preferences preferences) {
        this.preferences = preferences;
        type = Type.Attention;

        useShutDownButton();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void show() {
        if (headLine == null)
            headLine = Res.get("torNetworkSettingWindow.header");

        width = 1000;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        addCloseButton();
        applyStyles();
        display();
    }

    protected void addCloseButton() {
        closeButton = new Button(closeButtonText == null ? Res.get("shared.close") : closeButtonText);
        closeButton.setOnAction(event -> doClose());

        if (actionHandlerOptional.isPresent()) {
            actionButton = new Button(Res.get("torNetworkSettingWindow.saveAndRestart"));
            actionButton.setDefaultButton(true);
            //TODO app wide focus
            //actionButton.requestFocus();
            actionButton.setOnAction(event -> saveAndShutDown());

            Button urlButton = new Button(Res.get("torNetworkSettingWindow.openTorWebPage"));
            urlButton.setOnAction(event -> {
                try {
                    Utilities.openURI(URI.create("https://bridges.torproject.org/bridges"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            Pane spacer = new Pane();
            HBox hBox = new HBox();
            hBox.setSpacing(10);
            hBox.getChildren().addAll(spacer, closeButton, urlButton, actionButton);
            HBox.setHgrow(spacer, Priority.ALWAYS);

            GridPane.setHalignment(hBox, HPos.RIGHT);
            GridPane.setRowIndex(hBox, ++rowIndex);
            GridPane.setColumnSpan(hBox, 2);
            GridPane.setMargin(hBox, new Insets(buttonDistance, 0, 0, 0));
            gridPane.getChildren().add(hBox);
        } else if (!hideCloseButton) {
            closeButton.setDefaultButton(true);
            GridPane.setHalignment(closeButton, HPos.RIGHT);
            GridPane.setMargin(closeButton, new Insets(buttonDistance, 0, 0, 0));
            GridPane.setRowIndex(closeButton, rowIndex);
            GridPane.setColumnIndex(closeButton, 1);
            gridPane.getChildren().add(closeButton);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                } else if (e.getCode() == KeyCode.ENTER) {
                    e.consume();
                    saveAndShutDown();
                }
            });
        }
    }

    private void addContent() {
        Label label = addLabel(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.info"));
        label.setWrapText(true);
        GridPane.setColumnIndex(label, 0);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        GridPane.setValignment(label, VPos.TOP);

        GridPane.setMargin(label, new Insets(0, 0, 20, 0));

        ToggleGroup toggleGroup = new ToggleGroup();
        toggleGroup.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
            @Override
            public void changed(ObservableValue<? extends Toggle> observable, Toggle oldValue, Toggle newValue) {
                if (newValue == noBridgesRadioButton) {
                    transportTypeLabel.setDisable(true);
                    transportTypeComboBox.setDisable(true);
                    enterBridgeLabel.setDisable(true);
                    bridgeEntriesTextArea.setDisable(true);

                    bridgeEntriesTextArea.setText("");
                    preferences.setBridgeAddresses(null);
                } else if (newValue == providedBridgesRadioButton) {
                    transportTypeLabel.setDisable(false);
                    transportTypeComboBox.setDisable(false);
                    enterBridgeLabel.setDisable(true);
                    bridgeEntriesTextArea.setDisable(true);

                    transportTypeComboBox.getSelectionModel().select(0);
                    preferences.setBridgeAddresses(DefaultPluggableTransports.OBFS_4);
                } else if (newValue == customBridgesRadioButton) {
                    enterBridgeLabel.setDisable(false);
                    bridgeEntriesTextArea.setDisable(false);
                    transportTypeLabel.setDisable(true);
                    transportTypeComboBox.setDisable(true);

                    bridgeEntriesTextArea.setText("");
                    preferences.setBridgeAddresses(null);
                }
            }
        });
        // noBridges
        noBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.noBridges"));

        // providedBridges
        providedBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.providedBridges"));
        final Tuple2<Label, ComboBox> labelComboBoxTuple2 = addLabelComboBox(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.transportType"));
        transportTypeLabel = labelComboBoxTuple2.first;
        transportTypeComboBox = labelComboBoxTuple2.second;
        transportTypeComboBox.setItems(FXCollections.observableArrayList(Arrays.asList(Res.get("torNetworkSettingWindow.obfs4"),
                Res.get("torNetworkSettingWindow.obfs3"),
                Res.get("torNetworkSettingWindow.meekAmazon"),
                Res.get("torNetworkSettingWindow.meekAzure"))));
        transportTypeComboBox.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (toggleGroup.getSelectedToggle() == providedBridgesRadioButton) {
                switch ((int) newValue) {
                    case 0:
                        preferences.setBridgeAddresses(DefaultPluggableTransports.OBFS_4);
                        break;
                    case 1:
                        preferences.setBridgeAddresses(DefaultPluggableTransports.OBFS_3);
                        break;
                    case 2:
                        preferences.setBridgeAddresses(DefaultPluggableTransports.MEEK_AMAZON);
                        break;
                    case 3:
                        preferences.setBridgeAddresses(DefaultPluggableTransports.MEEK_AZURE);
                        break;
                }
            }
        });

        // customBridges
        customBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.customBridges"));

       /* enterBridgeLabel = addLabel(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.enterBridge"));
        enterBridgeLabel.setWrapText(true);
        GridPane.setColumnSpan(enterBridgeLabel, 2);
        GridPane.setHalignment(enterBridgeLabel, HPos.LEFT);*/

        final Tuple2<Label, TextArea> labelTextAreaTuple2 = addLabelTextArea(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.enterBridge"), Res.get("torNetworkSettingWindow.enterBridgePrompt"));
        enterBridgeLabel = labelTextAreaTuple2.first;
        bridgeEntriesTextArea = labelTextAreaTuple2.second;
        bridgeEntriesTextArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (toggleGroup.getSelectedToggle() == customBridgesRadioButton) {
                if (newValue != null) {
                    preferences.setBridgeAddresses(Arrays.asList(bridgeEntriesTextArea.getText().split("\\n")));
                } else {
                    preferences.setBridgeAddresses(null);
                }
            }
        });

        Label label2 = addLabel(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.restartInfo"));
        label2.setWrapText(true);
        GridPane.setColumnIndex(label2, 1);
        GridPane.setColumnSpan(label2, 2);
        GridPane.setHalignment(label2, HPos.RIGHT);
        GridPane.setValignment(label, VPos.TOP);

        GridPane.setMargin(label, new Insets(10, 10, 20, 0));

        toggleGroup.selectToggle(providedBridgesRadioButton);
    }

    private void saveAndShutDown() {
        UserThread.runAfter(() -> {
            actionHandlerOptional.ifPresent(Runnable::run);
        }, 500, TimeUnit.MILLISECONDS);
        hide();
    }
}
