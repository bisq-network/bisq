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

package io.bitsquare.gui.main.preferences.network;

import io.bitsquare.gui.CachedViewCB;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This UI is not cached as it is normally only needed once.
 */
public class NetworkPreferencesViewCB extends CachedViewCB<NetworkPreferencesPM> {

    private static final Logger log = LoggerFactory.getLogger(NetworkPreferencesViewCB.class);

    @FXML TextField bitcoinNetworkType, p2pNetworkConnection, p2pNetworkAddress, bootstrapAddress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private NetworkPreferencesViewCB(NetworkPreferencesPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        bitcoinNetworkType.setText(presentationModel.bitcoinNetworkType());
        p2pNetworkConnection.setText(presentationModel.p2pNetworkConnection());
        p2pNetworkAddress.setText(presentationModel.p2pNetworkAddress());
        bootstrapAddress.setText(presentationModel.bootstrapAddress());
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

}

