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

package io.bitsquare.gui.main.account.content.seedwords;

import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.gui.main.account.setup.AccountSetupViewCB;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeedWordsViewCB extends CachedCodeBehind<SeedWordsPM> implements ContextAware {

    private static final Logger log = LoggerFactory.getLogger(SeedWordsViewCB.class);

    @FXML private Button completedButton;
    @FXML private TextArea seedWordsTextArea;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private SeedWordsViewCB(SeedWordsPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        seedWordsTextArea.setText(presentationModel.seedWords.get());
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Override 
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void useSettingsContext(boolean useSettingsContext) {
        if (useSettingsContext)
            ((GridPane) root).getChildren().remove(completedButton);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    private void onCompleted() {
        if (parentController instanceof AccountSetupViewCB)
            ((AccountSetupViewCB) parentController).onCompleted(this);
    }

    @FXML
    private void onOpenHelp() {
        Help.openWindow(HelpId.SETUP_SEED_WORDS);
    }
}

