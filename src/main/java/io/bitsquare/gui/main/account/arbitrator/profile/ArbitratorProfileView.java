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

package io.bitsquare.gui.main.account.arbitrator.profile;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.gui.AbstractView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Preferences;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;

// TODO Arbitration is very basic yet
public class ArbitratorProfileView extends AbstractView {

    private final Preferences preferences;

    private final Persistence persistence;
    private final BSFormatter formatter;


    @FXML Label nameLabel;
    @FXML TextField nameTextField, languagesTextField, reputationTextField,
            feeTextField, methodsTextField, passiveServiceFeeTextField,
            idVerificationsTextField, webPageTextField, maxTradeVolumeTextField;
    @FXML TextArea descriptionTextArea;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorProfileView(Preferences preferences, Persistence persistence,
                                 BSFormatter formatter) {
        this.preferences = preferences;
        this.persistence = persistence;

        //  ApplicationPreferences persistedApplicationPreferences = (ApplicationPreferences) storage
        // .read(settings.getClass().getName());
        // settings.applyPersistedSettings(persistedApplicationPreferences);
        this.formatter = formatter;
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyArbitrator(Arbitrator arbitrator) {
        if (arbitrator != null && arbitrator.getIdType() != null) {
            String name = "";
            switch (arbitrator.getIdType()) {
                case REAL_LIFE_ID:
                    name = "Name:";
                    break;
                case NICKNAME:
                    name = "Nickname:";
                    break;
                case COMPANY:
                    name = "Company:";
                    break;
            }
            nameLabel.setText(name);

            nameTextField.setText(arbitrator.getName());
            languagesTextField.setText(formatter.languageLocalesToString(arbitrator.getLanguages()));
            reputationTextField.setText(arbitrator.getReputation().toString());
            feeTextField.setText(String.valueOf(arbitrator.getFee() + " BTC"));
            methodsTextField.setText(formatter.arbitrationMethodsToString(arbitrator.getArbitrationMethods()));
            idVerificationsTextField.setText(
                    formatter.arbitrationIDVerificationsToString(arbitrator.getIdVerifications()));
            webPageTextField.setText(arbitrator.getWebUrl());
            descriptionTextArea.setText(arbitrator.getDescription());
        }
    }

}

