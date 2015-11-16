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

package io.bitsquare.gui.popups;

import io.bitsquare.user.Preferences;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.addCheckBox;

public class FirstTimeWebViewPopup extends WebViewPopup {
    private static final Logger log = LoggerFactory.getLogger(FirstTimeWebViewPopup.class);
    private Preferences preferences;
    private String id;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FirstTimeWebViewPopup(Preferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public FirstTimeWebViewPopup url(String url) {
        super.url(url);
        return this;
    }

    public FirstTimeWebViewPopup onClose(Runnable closeHandler) {
        this.closeHandlerOptional = Optional.of(closeHandler);
        return this;
    }

    public FirstTimeWebViewPopup id(String id) {
        this.id = id;
        return this;
    }

    @Override
    protected void addHtmlContent() {
        super.addHtmlContent();

        CheckBox dontShowAgain = addCheckBox(gridPane, ++rowIndex, "Don't show again", 10);
        dontShowAgain.setOnAction(e -> {
            if (dontShowAgain.isSelected())
                preferences.dontShowAgain(id);
        });
        closeButton = new Button("Close");
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });

        GridPane.setRowIndex(closeButton, ++rowIndex);
        GridPane.setColumnIndex(closeButton, 1);
        gridPane.getChildren().add(closeButton);
        GridPane.setMargin(closeButton, new Insets(10, 0, 0, 0));
    }


}
