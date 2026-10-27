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


package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;

import bisq.core.app.TorSetup;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import bisq.network.p2p.network.BridgeOption;
import bisq.network.p2p.network.BridgeTransport;
import bisq.network.p2p.network.BuiltInBridges;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.config.ConfigFileEditor;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple4;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.net.URI;

import java.io.IOException;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;

@Slf4j
@Singleton
public class TorNetworkSettingsWindow extends Overlay<TorNetworkSettingsWindow> {

    private final Preferences preferences;
    private final NetworkNode networkNode;
    private final TorSetup torSetup;
    private final Config config;
    private final ConfigFileEditor configFileEditor;
    // The value which the next start uses, unless a command line option overrides the config file
    private boolean allowPluggableTransports;
    private Label enterBridgeLabel;
    private ComboBox<BridgeTransport> transportTypeComboBox;
    private TextArea bridgeEntriesTextArea;
    private BridgeOption selectedBridgeOption = BridgeOption.NONE;
    private BridgeTransport selectedTorTransport = BridgeTransport.OBFS_4;
    private String customBridges = "";
    // Why the start of Tor failed, if these settings can fix it
    @Nullable
    private String startFailure;

    @Inject
    public TorNetworkSettingsWindow(Preferences preferences,
                                    NetworkNode networkNode,
                                    TorSetup torSetup,
                                    Config config) {
        this.preferences = preferences;
        this.networkNode = networkNode;
        this.torSetup = torSetup;
        this.config = config;
        this.configFileEditor = new ConfigFileEditor(config.getConfigFile());
        this.allowPluggableTransports = config.allowPluggableTransportsInTorSandbox;

        type = Type.Attention;

        useShutDownButton();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void show() {
        if (!isDisplayed) {
            if (headLine == null)
                headLine = Res.get("torNetworkSettingWindow.header");

            width = 1068;
            rowIndex = 0;
            createGridPane();
            gridPane.getColumnConstraints().get(0).setHalignment(HPos.LEFT);

            addContent();
            addButtons();
            applyStyles();
            display();
        }
    }

    public TorNetworkSettingsWindow startFailure(@Nullable String startFailure) {
        this.startFailure = startFailure;
        return this;
    }

    protected void addButtons() {
        closeButton = new AutoTooltipButton(closeButtonText == null ? Res.get("shared.close") : closeButtonText);
        closeButton.setOnAction(event -> doClose());

        if (actionHandlerOptional.isPresent()) {
            actionButton = new AutoTooltipButton(Res.get("shared.shutDown"));
            actionButton.setDefaultButton(true);
            //TODO app wide focus
            //actionButton.requestFocus();
            actionButton.setOnAction(event -> saveAndShutDown());

            Button urlButton = new AutoTooltipButton(Res.get("torNetworkSettingWindow.openTorWebPage"));
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
            hBox.getChildren().addAll(spacer, urlButton, closeButton, actionButton);
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

    @Override
    protected void applyStyles() {
        super.applyStyles();
        gridPane.setId("popup-grid-pane-bg");
    }

    private void addContent() {
        if (startFailure != null) {
            Label startFailureLabel = addLabel(gridPane, ++rowIndex, startFailure);
            startFailureLabel.setWrapText(true);
            startFailureLabel.getStyleClass().add("error-text");
            GridPane.setColumnSpan(startFailureLabel, 2);
            GridPane.setHalignment(startFailureLabel, HPos.LEFT);
            GridPane.setMargin(startFailureLabel, new Insets(0, 0, Layout.FIRST_ROW_DISTANCE, 0));
        }

        addTitledGroupBg(gridPane, ++rowIndex, 2, Res.get("torNetworkSettingWindow.deleteFiles.header"));

        Label deleteFilesLabel = addLabel(gridPane, rowIndex, Res.get("torNetworkSettingWindow.deleteFiles.info"), Layout.TWICE_FIRST_ROW_DISTANCE);
        deleteFilesLabel.setWrapText(true);
        GridPane.setColumnIndex(deleteFilesLabel, 0);
        GridPane.setColumnSpan(deleteFilesLabel, 2);
        GridPane.setHalignment(deleteFilesLabel, HPos.LEFT);
        GridPane.setValignment(deleteFilesLabel, VPos.TOP);

        Tuple4<Button, BusyAnimation, Label, HBox> tuple = addButtonBusyAnimationLabelAfterGroup(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.deleteFiles.button"));
        Button deleteFilesButton = tuple.first;
        deleteFilesButton.getStyleClass().remove("action-button");
        deleteFilesButton.setOnAction(e -> {
            tuple.second.play();
            tuple.third.setText(Res.get("torNetworkSettingWindow.deleteFiles.progress"));
            gridPane.setMouseTransparent(true);
            deleteFilesButton.setDisable(true);
            cleanTorDir(() -> {
                tuple.second.stop();
                tuple.third.setText("");
                new Popup().feedback(Res.get("torNetworkSettingWindow.deleteFiles.success"))
                        .useShutDownButton()
                        .hideCloseButton()
                        .show();
            });
        });


        final TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++rowIndex, 10, Res.get("torNetworkSettingWindow.bridges.header"), Layout.GROUP_DISTANCE);
        titledGroupBg.getStyleClass().add("last");

        Label bridgesLabel = addLabel(gridPane, rowIndex, Res.get("torNetworkSettingWindow.bridges.info"), Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE);
        bridgesLabel.setWrapText(true);
        GridPane.setColumnIndex(bridgesLabel, 0);
        GridPane.setColumnSpan(bridgesLabel, 2);
        GridPane.setHalignment(bridgesLabel, HPos.LEFT);
        GridPane.setValignment(bridgesLabel, VPos.TOP);

        ToggleGroup toggleGroup = new ToggleGroup();

        // noBridges
        RadioButton noBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.noBridges"));
        noBridgesRadioButton.setUserData(BridgeOption.NONE);
        GridPane.setMargin(noBridgesRadioButton, new Insets(20, 0, 0, 0));

        // providedBridges
        RadioButton providedBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.providedBridges"));
        providedBridgesRadioButton.setUserData(BridgeOption.PROVIDED);
        transportTypeComboBox = addComboBox(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.transportType"));
        transportTypeComboBox.setItems(FXCollections.observableArrayList(BridgeTransport.values()));
        transportTypeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(BridgeTransport transport) {
                switch (transport) {
                    case SNOWFLAKE:
                        return Res.get("torNetworkSettingWindow.snowflake");
                    case MEEK:
                        return Res.get("torNetworkSettingWindow.meek");
                    default:
                    case OBFS_4:
                        return Res.get("torNetworkSettingWindow.obfs4");
                }
            }

            @Override
            public BridgeTransport fromString(String string) {
                return null;
            }
        });

        // customBridges
        RadioButton customBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.customBridges"));
        customBridgesRadioButton.setUserData(BridgeOption.CUSTOM);

        final Tuple2<Label, TextArea> labelTextAreaTuple2 = addTopLabelTextArea(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.enterBridge"), Res.get("torNetworkSettingWindow.enterBridgeLinePrompt"));
        enterBridgeLabel = labelTextAreaTuple2.first;
        bridgeEntriesTextArea = labelTextAreaTuple2.second;
        bridgeEntriesTextArea.setPrefHeight(60);

        // Tor reads the option when it starts, as it reads the bridges.
        ToggleButton allowPluggableTransportsToggle = addSlideToggleButton(gridPane, ++rowIndex,
                Res.get("torNetworkSettingWindow.allowPluggableTransports"), 10);
        GridPane.setColumnSpan(allowPluggableTransportsToggle, 2);
        allowPluggableTransportsToggle.setSelected(allowPluggableTransports);
        Label allowPluggableTransportsInfo = addLabel(gridPane, ++rowIndex, getAllowPluggableTransportsInfo());
        allowPluggableTransportsInfo.setWrapText(true);
        GridPane.setColumnSpan(allowPluggableTransportsInfo, 2);
        GridPane.setHalignment(allowPluggableTransportsInfo, HPos.LEFT);
        // Without the sandbox Tor starts the transports anyway, and on Windows the Tor library refuses them in the
        // sandbox. An option on the command line overrides what the switch writes into the config file.
        allowPluggableTransportsToggle.setDisable(!config.useTorSandbox || Utilities.isWindows() ||
                config.allowPluggableTransportsInTorSandboxSetOnCommandLine);
        allowPluggableTransportsToggle.setOnAction(e -> {
            if (allowPluggableTransportsToggle.isSelected()) {
                // Only the action button allows them. Closing the popup, also with Escape or Enter, keeps them off.
                new Popup().warning(Res.get("torNetworkSettingWindow.allowPluggableTransports.warn"))
                        .actionButtonText(Res.get("torNetworkSettingWindow.allowPluggableTransports.warn.accept"))
                        .onAction(() -> setAllowPluggableTransports(true))
                        .closeButtonText(Res.get("torNetworkSettingWindow.allowPluggableTransports.warn.cancel"))
                        .onClose(() -> allowPluggableTransportsToggle.setSelected(false))
                        .show();
            } else {
                setAllowPluggableTransports(false);
            }
        });

        Label label2 = addLabel(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.restartInfo"));
        label2.setWrapText(true);
        GridPane.setColumnSpan(label2, 2);
        GridPane.setHalignment(label2, HPos.LEFT);
        GridPane.setValignment(label2, VPos.TOP);
        GridPane.setMargin(label2, new Insets(10, 10, 20, 0));

        // init persisted values
        selectedBridgeOption = preferences.getBridgeOption();
        selectedTorTransport = preferences.getBridgeTransport();
        customBridges = preferences.getCustomBridges();
        switch (selectedBridgeOption) {
            case PROVIDED:
                toggleGroup.selectToggle(providedBridgesRadioButton);
                break;
            case CUSTOM:
                toggleGroup.selectToggle(customBridgesRadioButton);
                break;
            default:
            case NONE:
                toggleGroup.selectToggle(noBridgesRadioButton);
                break;
        }
        applyToggleSelection();

        transportTypeComboBox.getSelectionModel().select(selectedTorTransport);
        bridgeEntriesTextArea.setText(customBridges);

        toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            selectedBridgeOption = (BridgeOption) newValue.getUserData();
            preferences.setBridgeOptionOrdinal(selectedBridgeOption.ordinal());
            applyToggleSelection();
        });
        transportTypeComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectedTorTransport = newValue;
            preferences.setTorTransportOrdinal(selectedTorTransport.ordinal());
            setBridgeAddressesByTransport();
        });
        bridgeEntriesTextArea.textProperty().addListener((observable, oldValue, newValue) -> {
                    customBridges = newValue;
                    preferences.setCustomBridges(customBridges);
                    setBridgeAddressesByCustomBridges();
                }
        );
    }

    private String getAllowPluggableTransportsInfo() {
        if (!config.useTorSandbox) {
            return Res.get("torNetworkSettingWindow.allowPluggableTransports.sandboxDisabled");
        }
        if (Utilities.isWindows()) {
            return Res.get("torNetworkSettingWindow.allowPluggableTransports.windows");
        }
        if (config.allowPluggableTransportsInTorSandboxSetOnCommandLine) {
            return Res.get("torNetworkSettingWindow.allowPluggableTransports.commandLine",
                    config.allowPluggableTransportsInTorSandbox);
        }
        return Res.get("torNetworkSettingWindow.allowPluggableTransports.info");
    }

    // Only this option, and off by clearing it, so that the config file keeps following its default
    private void setAllowPluggableTransports(boolean allow) {
        allowPluggableTransports = allow;
        if (allow) {
            configFileEditor.setOption(Config.ALLOW_PLUGGABLE_TRANSPORTS_IN_TOR_SANDBOX, "true");
        } else {
            configFileEditor.clearOption(Config.ALLOW_PLUGGABLE_TRANSPORTS_IN_TOR_SANDBOX);
        }
    }

    private void cleanTorDir(Runnable resultHandler) {
        // We shut down Tor to be able to delete locked files (Windows locks files used by a process)
        networkNode.shutDown(() -> {
            // We give it a bit extra time to be sure that OS locks are removed
            UserThread.runAfter(() -> {
                torSetup.cleanupTorFiles(resultHandler, errorMessage -> new Popup().error(errorMessage).show());
            }, 3);
        });
    }

    private void applyToggleSelection() {
        switch (selectedBridgeOption) {
            case PROVIDED:
                transportTypeComboBox.setDisable(false);
                enterBridgeLabel.setDisable(true);
                bridgeEntriesTextArea.setDisable(true);

                setBridgeAddressesByTransport();
                break;
            case CUSTOM:
                enterBridgeLabel.setDisable(false);
                bridgeEntriesTextArea.setDisable(false);
                transportTypeComboBox.setDisable(true);

                setBridgeAddressesByCustomBridges();
                break;
            default:
            case NONE:
                transportTypeComboBox.setDisable(true);
                enterBridgeLabel.setDisable(true);
                bridgeEntriesTextArea.setDisable(true);

                preferences.setBridgeAddresses(null);
                break;
        }
    }

    // The node uses the built-in bridges of the installed Tor bundle and reads only the selected type. An older version
    // reads only the stored bridge lines, so they are stored for a downgrade.
    private void setBridgeAddressesByTransport() {
        List<String> builtInBridges = BuiltInBridges.readInstalled(torSetup.getTorDir(), selectedTorTransport);
        preferences.setBridgeAddresses(builtInBridges.isEmpty() ? null : builtInBridges);
    }

    private void setBridgeAddressesByCustomBridges() {
        preferences.setBridgeAddresses(customBridges != null ? Arrays.asList(customBridges.split("\\n")) : null);
    }

    private void saveAndShutDown() {
        UserThread.runAfter(() -> actionHandlerOptional.ifPresent(Runnable::run), 500, TimeUnit.MILLISECONDS);
        hide();
    }
}
