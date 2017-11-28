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
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static io.bisq.gui.util.FormBuilder.*;

@Slf4j
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
    private RadioButton noBridgesRadioButton, providedBridgesRadioButton, customBridgesRadioButton;
    private Label enterBridgeLabel;
    private ComboBox<Transport> transportTypeComboBox;
    private TextArea bridgeEntriesTextArea;
    private Label transportTypeLabel;
    private BridgeOption selectedBridgeOption = BridgeOption.NONE;
    private Transport selectedTorTransportOrdinal = Transport.OBFS_4;
    private String customBridges = "";

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
            actionButton = new Button(Res.get("shared.shutDown"));
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

    private void addContent() {
        gridPane.setStyle("-fx-background-color: #f8f8f8;");

        Label label = addLabel(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.info"));
        label.setWrapText(true);
        GridPane.setColumnIndex(label, 0);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        GridPane.setValignment(label, VPos.TOP);

        GridPane.setMargin(label, new Insets(0, 0, 20, 0));

        ToggleGroup toggleGroup = new ToggleGroup();

        // noBridges
        noBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.noBridges"));
        noBridgesRadioButton.setUserData(BridgeOption.NONE);

        // providedBridges
        providedBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.providedBridges"));
        providedBridgesRadioButton.setUserData(BridgeOption.PROVIDED);
        final Tuple2<Label, ComboBox> labelComboBoxTuple2 = addLabelComboBox(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.transportType"));
        transportTypeLabel = labelComboBoxTuple2.first;
        transportTypeComboBox = labelComboBoxTuple2.second;
        transportTypeComboBox.setItems(FXCollections.observableArrayList(Arrays.asList(
                Transport.OBFS_4,
                Transport.OBFS_3,
                Transport.MEEK_AMAZON,
                Transport.MEEK_AZURE)));
        transportTypeComboBox.setConverter(new StringConverter<Transport>() {
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
        customBridgesRadioButton = addRadioButton(gridPane, ++rowIndex, toggleGroup, Res.get("torNetworkSettingWindow.customBridges"));
        customBridgesRadioButton.setUserData(BridgeOption.CUSTOM);

        final Tuple2<Label, TextArea> labelTextAreaTuple2 = addLabelTextArea(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.enterBridge"), Res.get("torNetworkSettingWindow.enterBridgePrompt"));
        enterBridgeLabel = labelTextAreaTuple2.first;
        bridgeEntriesTextArea = labelTextAreaTuple2.second;

        Label label2 = addLabel(gridPane, ++rowIndex, Res.get("torNetworkSettingWindow.restartInfo"));
        label2.setWrapText(true);
        GridPane.setColumnIndex(label2, 1);
        GridPane.setColumnSpan(label2, 2);
        GridPane.setHalignment(label2, HPos.LEFT);
        GridPane.setValignment(label, VPos.TOP);
        GridPane.setMargin(label, new Insets(10, 10, 20, 0));

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

    private void applyToggleSelection() {
        switch (selectedBridgeOption) {
            case PROVIDED:
                transportTypeLabel.setDisable(false);
                transportTypeComboBox.setDisable(false);
                enterBridgeLabel.setDisable(true);
                bridgeEntriesTextArea.setDisable(true);

                setBridgeAddressesByTransport();
                break;
            case CUSTOM:
                enterBridgeLabel.setDisable(false);
                bridgeEntriesTextArea.setDisable(false);
                transportTypeLabel.setDisable(true);
                transportTypeComboBox.setDisable(true);

                setBridgeAddressesByCustomBridges();
                break;
            default:
            case NONE:
                transportTypeLabel.setDisable(true);
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
        UserThread.runAfter(() -> {
            actionHandlerOptional.ifPresent(Runnable::run);
        }, 500, TimeUnit.MILLISECONDS);
        hide();
    }
}
