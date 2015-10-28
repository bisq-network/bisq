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

package io.bitsquare.alert;

import com.google.inject.Inject;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.p2p.storage.HashSetChangedListener;
import io.bitsquare.p2p.storage.data.ProtectedData;
import io.bitsquare.user.User;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.SignatureException;

import static org.bitcoinj.core.Utils.HEX;

public class AlertManager {
    transient private static final Logger log = LoggerFactory.getLogger(AlertManager.class);

    private final AlertService alertService;
    private KeyRing keyRing;
    private User user;
    private final ObjectProperty<Alert> alertMessageProperty = new SimpleObjectProperty<>();

    // Pub key for developer global alert message
    private static final String devPubKeyAsHex = "02682880ae61fc1ea9375198bf2b5594fc3ed28074d3f5f0ed907e38acc5fb1fdc";
    private ECKey alertSigningKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AlertManager(AlertService alertService, KeyRing keyRing, User user) {
        this.alertService = alertService;
        this.keyRing = keyRing;
        this.user = user;

        alertService.addHashSetChangedListener(new HashSetChangedListener() {
            @Override
            public void onAdded(ProtectedData entry) {
                Serializable data = entry.expirablePayload;
                if (data instanceof Alert) {
                    Alert alert = (Alert) data;
                    if (verifySignature(alert))
                        alertMessageProperty.set(alert);
                }
            }

            @Override
            public void onRemoved(ProtectedData entry) {
                Serializable data = entry.expirablePayload;
                if (data instanceof Alert) {
                    Alert alert = (Alert) data;
                    if (verifySignature(alert))
                        alertMessageProperty.set(null);
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyObjectProperty<Alert> alertMessageProperty() {
        return alertMessageProperty;
    }

    public boolean addAlertMessageIfKeyIsValid(Alert alert, String privKeyString) {
        // if there is a previous message we remove that first
        if (user.getDevelopersAlert() != null)
            removeAlertMessageIfKeyIsValid(privKeyString);

        boolean isKeyValid = isKeyValid(privKeyString);
        if (isKeyValid) {
            signAndAddSignatureToAlertMessage(alert);
            user.setDevelopersAlert(alert);
            alertService.addAlertMessage(alert, null, null);
        }
        return isKeyValid;
    }

    public boolean removeAlertMessageIfKeyIsValid(String privKeyString) {
        Alert developersAlert = user.getDevelopersAlert();
        if (isKeyValid(privKeyString) && developersAlert != null) {
            alertService.removeAlertMessage(developersAlert, null, null);
            user.setDevelopersAlert(null);
            return true;
        } else {
            return false;
        }
    }

    private boolean isKeyValid(String privKeyString) {
        try {
            alertSigningKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyString)));
            return devPubKeyAsHex.equals(Utils.HEX.encode(alertSigningKey.getPubKey()));
        } catch (Throwable t) {
            return false;
        }
    }

    private void signAndAddSignatureToAlertMessage(Alert alert) {
        String alertMessageAsHex = Utils.HEX.encode(alert.message.getBytes());
        String signatureAsBase64 = alertSigningKey.signMessage(alertMessageAsHex);
        alert.setSigAndStoragePubKey(signatureAsBase64, keyRing.getStorageSignatureKeyPair().getPublic());
    }

    private boolean verifySignature(Alert alert) {
        String alertMessageAsHex = Utils.HEX.encode(alert.message.getBytes());
        try {
            ECKey.fromPublicOnly(HEX.decode(devPubKeyAsHex)).verifyMessage(alertMessageAsHex, alert.getSignatureAsBase64());
            return true;
        } catch (SignatureException e) {
            log.warn("verifySignature failed");
            return false;
        }
    }
}
