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

package io.bisq.gui.main.overlays.windows;

import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.core.user.Preferences;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.util.GUIUtil;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;

import javax.inject.Inject;

import static io.bisq.gui.util.FormBuilder.*;

//TODO not used yet but keep it for later
public class AddBitcoinNodesWindow extends Overlay<AddBitcoinNodesWindow> {
    private final Preferences preferences;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AddBitcoinNodesWindow(Preferences preferences) {
        this.preferences = preferences;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = "Protect your privacy";

        width = 900;
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

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    private void addContent() {
        //TODO not added to resource  files
        Label label = addLabel(gridPane, ++rowIndex, "For the best protection of your privacy is it recommended that you run your own " + Res.getBaseCurrencyName() + " core node.\n" +
                "You can run it locally (127.0.0.1) or hosted on a VPS.\n" +
                "You can edit that settings in \"Settings/Network info\".\n\n" +
                "If you prefer to use the public " + Res.getBaseCurrencyName() + " network your Bitcoin transactions might get de-anonymized by chain analysis companies operating full nodes to spy on Bitcoin users.\n\n" +
                "To learn more about that topic please read our FAQ on bisq.network.");
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);

        HyperlinkWithIcon hyperlinkWithIcon = new HyperlinkWithIcon("Open bisq FAQ", AwesomeIcon.EXTERNAL_LINK);
        hyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage("https://bisq.network/faq/#privacy_btc"));
        GridPane.setRowIndex(hyperlinkWithIcon, ++rowIndex);
        GridPane.setColumnIndex(hyperlinkWithIcon, 0);
        GridPane.setMargin(hyperlinkWithIcon, new Insets(0, 0, 0, -4));
        GridPane.setHalignment(hyperlinkWithIcon, HPos.LEFT);
        gridPane.getChildren().add(hyperlinkWithIcon);

        Tuple2<Label, InputTextField> labelInputTextFieldTuple2 = addLabelInputTextField(gridPane, ++rowIndex,
                "Add custom " + Res.getBaseCurrencyName() + " nodes:", 20);
        InputTextField input = labelInputTextFieldTuple2.second;
        input.setPromptText("Add comma separated IP addresses");
        if (!preferences.getBitcoinNodes().isEmpty())
            input.setText(preferences.getBitcoinNodes());

        Tuple2<Button, Button> tuple = add2Buttons(gridPane, ++rowIndex, "Save",
                "Ignore and use public " + Res.getBaseCurrencyName() + " network nodes");
        Button saveButton = tuple.first;
        saveButton.setOnAction(e -> {
            preferences.setBitcoinNodes(input.getText());
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        closeButton = tuple.second;
        closeButton.setOnAction(e -> {
            preferences.setBitcoinNodes("");
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        // Add some space
        ++rowIndex;
    }
}
