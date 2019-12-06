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

import bisq.network.p2p.network.DefaultPluggableTransports;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.UserThread;
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
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.*;

@Slf4j
@Singleton
public class TorNetworkSettingsWindow extends Overlay<TorNetworkSettingsWindow> {

    public enum BridgeOption {
        NONE,
        PROVIDED,
        CUSTOM
    }

    public enum Transport {
        OBFS_4,
        OBFS_3,
        MEEK_AMAZON,
        MEEK_AZURE
    }

    private final Preferences preferences;
    private final NetworkNode networkNode;
    private final TorSetup torSetup;
    private Label enterBridgeLabel;
    private ComboBox<Transport> transportTypeComboBox;
    private TextArea bridgeEntriesTextArea;
    private BridgeOption selectedBridgeOption = BridgeOption.NONE;
    private Transport selectedTorTransportOrdinal = Transport.OBFS_4;
    private String customBridges = "";

    @Inject
    public TorNetworkSettingsWindow(Preferences preferences,
                                    NetworkNode networkNode,
                                    TorSetup torSetup) {
        this.preferences = preferences;
        this.networkNode = networkNode;
        this.torSetup = torSetup;

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

            createGridPane();
            gridPane.getColumnConstraints().get(0).setHalignment(HPos.LEFT);

            addContent();
            addButtons();
            applyStyles();
            display();
        }
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


        final TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++rowIndex, 8, Res.get("torNetworkSettingWindow.bridges.header"), Layout.GROUP_DISTANCE);
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
        transportTypeComboBox.setItems(FXCollections.observableArrayList(Arrays.asList(
                Transport.OBFS_4,
                Transport.OBFS_3,
                Transport.MEEK_AMAZON,
                Transport.MEEK_AZURE)));
        transportTypeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Transport transport) {
                switch (transport) {
                    case OBFS_3:
                        return Res.get("torNetworkSettingWindow.obfs3");
                    case MEEK_AMAZON:
                        return Res.get("torNetworkSettingWindow.meekAmazon");
                    case MEEK_AZURE:
                        return Res.get("torNetworkSettingWindow.meekAzure");
                    default:
                    case OBFS_4:
                        return Res.get("torNetworkSettingWindow.obfs4");
                }
            }

            @Override
            public Transport fromString(String string) {
                return null;
            }
        });

        // customBridges
        RadioButton customBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.customBridges"));
        customBridgesRadioButton.setUserData(BridgeOption.CUSTOM);

        final Tuple2<Label, TextArea> labelTextAreaTuple2 = addTopLabelTextArea(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.enterBridge"), Res.get("torNetworkSettingWindow.enterBridgePrompt"));
        enterBridgeLabel = labelTextAreaTuple2.first;
        bridgeEntriesTextArea = labelTextAreaTuple2.second;
        bridgeEntriesTextArea.setPrefHeight(60);

        Label label2 = addLabel(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.restartInfo"));
        label2.setWrapText(true);
        GridPane.setColumnSpan(label2, 2);
        GridPane.setHalignment(label2, HPos.LEFT);
        GridPane.setValignment(label2, VPos.TOP);
        GridPane.setMargin(label2, new Insets(10, 10, 20, 0));

        // init persisted values
        selectedBridgeOption = BridgeOption.values()[preferences.getBridgeOptionOrdinal()];
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

        selectedTorTransportOrdinal = Transport.values()[preferences.getTorTransportOrdinal()];
        transportTypeComboBox.getSelectionModel().select(selectedTorTransportOrdinal);

        customBridges = preferences.getCustomBridges();
        bridgeEntriesTextArea.setText(customBridges);

        toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            selectedBridgeOption = (BridgeOption) newValue.getUserData();
            preferences.setBridgeOptionOrdinal(selectedBridgeOption.ordinal());
            applyToggleSelection();
        });
        transportTypeComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectedTorTransportOrdinal = newValue;
            preferences.setTorTransportOrdinal(selectedTorTransportOrdinal.ordinal());
            setBridgeAddressesByTransport();
        });
        bridgeEntriesTextArea.textProperty().addListener((observable, oldValue, newValue) -> {
                    customBridges = newValue;
                    preferences.setCustomBridges(customBridges);
                    setBridgeAddressesByCustomBridges();
                }
        );
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

    private void setBridgeAddressesByTransport() {
        switch (selectedTorTransportOrdinal) {
            case OBFS_3:
                preferences.setBridgeAddresses(DefaultPluggableTransports.OBFS_3);
                break;
            case MEEK_AMAZON:
                preferences.setBridgeAddresses(DefaultPluggableTransports.MEEK_AMAZON);
                break;
            case MEEK_AZURE:
                preferences.setBridgeAddresses(DefaultPluggableTransports.MEEK_AZURE);
                break;
            default:
            case OBFS_4:
                preferences.setBridgeAddresses(DefaultPluggableTransports.OBFS_4);
                break;
        }
    }

    private void setBridgeAddressesByCustomBridges() {
        preferences.setBridgeAddresses(customBridges != null ? Arrays.asList(customBridges.split("\\n")) : null);
    }

    private void saveAndShutDown() {
        UserThread.runAfter(() -> actionHandlerOptional.ifPresent(Runnable::run), 500, TimeUnit.MILLISECONDS);
        hide();
    }
}
