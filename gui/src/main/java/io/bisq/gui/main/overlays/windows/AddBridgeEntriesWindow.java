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


/** This file is part of bisq.
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

package io.bisq.gui.main.overlays.windows;

import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import io.bisq.core.alert.Alert;
import io.bisq.core.user.Preferences;
import io.bisq.gui.main.overlays.Overlay;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static io.bisq.gui.util.FormBuilder.addLabel;
import static io.bisq.gui.util.FormBuilder.addLabelTextArea;

@Slf4j
public class AddBridgeEntriesWindow extends Overlay<AddBridgeEntriesWindow> {
    private TextArea bridgeEntriesTextArea;
    private final Preferences preferences;

    @Inject
    public AddBridgeEntriesWindow(Preferences preferences) {
        this.preferences = preferences;
        type = Type.Attention;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void show() {
        if (headLine == null)
            headLine = "Add Tor bridge entries";

        width = 900;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        addCloseButton();
        applyStyles();
        display();
    }

    protected void addCloseButton() {
        closeButton = new Button(closeButtonText == null ? "Close" : closeButtonText);
        closeButton.setOnAction(event -> doClose());

        if (actionHandlerOptional.isPresent() || actionButtonText != null) {
            actionButton = new Button("Save and retry");
            actionButton.setDefaultButton(true);
            //TODO app wide focus
            //actionButton.requestFocus();
            actionButton.setOnAction(event -> save());

            Button urlButton = new Button("Open Tor project web page");
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
            GridPane.setRowIndex(hBox, rowIndex);
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
                    save();
                }
            });
        }
    }

    private void addContent() {
        Label label = addLabel(gridPane, ++rowIndex, "We could not connect to the Tor network.\n" +
                "If Tor is blocked at your internet provider, you can try to add Tor bridge address entries from the Tor project:\n" +
                "https://bridges.torproject.org/bridges\n\n" +
                "Add one address entry in each line.\n");
        GridPane.setColumnIndex(label, 0);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        Tuple2<Label, TextArea> labelTextAreaTuple2 = addLabelTextArea(gridPane, rowIndex, "Bridge entries:", "");
        bridgeEntriesTextArea = labelTextAreaTuple2.second;
    }

    private void save() {
        if (!bridgeEntriesTextArea.getText().isEmpty()) {
            List<String> list = Arrays.asList(bridgeEntriesTextArea.getText().split("\\n"));
            preferences.setBridgeAddresses(list);
            actionHandlerOptional.ifPresent(Runnable::run);
            hide();
        }
    }
}