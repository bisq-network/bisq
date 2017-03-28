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

package io.bisq.core.alert;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bisq.common.app.DevEnv;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.user.User;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.P2PService;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.payload.StoragePayload;
import io.bisq.protobuffer.payload.alert.AlertPayload;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedStorageEntry;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SignatureException;

import static org.bitcoinj.core.Utils.HEX;

public class AlertManager {
    private static final Logger log = LoggerFactory.getLogger(AlertManager.class);

    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final User user;
    private final ObjectProperty<Alert> alertMessageProperty = new SimpleObjectProperty<>();

    // Pub key for developer global alert message
    private static final String pubKeyAsHex = DevEnv.USE_DEV_PRIVILEGE_KEYS ?
            DevEnv.DEV_PRIVILEGE_PUB_KEY :
            "036d8a1dfcb406886037d2381da006358722823e1940acc2598c844bbc0fd1026f";
    private ECKey alertSigningKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AlertManager(P2PService p2PService, KeyRing keyRing, User user, @Named(AppOptionKeys.IGNORE_DEV_MSG_KEY) boolean ignoreDevMsg) {
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.user = user;

        if (!ignoreDevMsg) {
            p2PService.addHashSetChangedListener(new HashMapChangedListener() {
                @Override
                public void onAdded(ProtectedStorageEntry data) {
                    final StoragePayload storagePayload = data.getStoragePayload();
                    if (storagePayload instanceof AlertPayload) {
                        Alert alert = new Alert(((AlertPayload) storagePayload).getAlertVO());
                        if (verifySignature(alert))
                            alertMessageProperty.set(alert);
                    }
                }

                @Override
                public void onRemoved(ProtectedStorageEntry data) {
                    final StoragePayload storagePayload = data.getStoragePayload();
                    if (storagePayload instanceof AlertPayload) {
                        Alert alert = new Alert(((AlertPayload) storagePayload).getAlertVO());
                        if (verifySignature(alert))
                            alertMessageProperty.set(null);
                    }
                }
            });
        }
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
            boolean result = p2PService.addData(new AlertPayload(alert.getAlertVO()), true);
            if (result) {
                log.trace("Add alertMessage to network was successful. AlertMessage = " + alert);
            }

        }
        return isKeyValid;
    }

    public boolean removeAlertMessageIfKeyIsValid(String privKeyString) {
        Alert alert = user.getDevelopersAlert();
        if (isKeyValid(privKeyString) && alert != null) {
            if (p2PService.removeData(new AlertPayload(alert.getAlertVO()), true))
                log.trace("Remove alertMessage from network was successful. AlertMessage = " + alert);

            user.setDevelopersAlert(null);
            return true;
        } else {
            return false;
        }
    }

    private boolean isKeyValid(String privKeyString) {
        try {
            alertSigningKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyString)));
            return pubKeyAsHex.equals(Utils.HEX.encode(alertSigningKey.getPubKey()));
        } catch (Throwable t) {
            return false;
        }
    }

    private void signAndAddSignatureToAlertMessage(Alert alert) {
        String alertMessageAsHex = Utils.HEX.encode(alert.getMessage().getBytes());
        String signatureAsBase64 = alertSigningKey.signMessage(alertMessageAsHex);
        alert.setSigAndPubKey(signatureAsBase64, keyRing.getSignatureKeyPair().getPublic());
    }

    private boolean verifySignature(Alert alert) {
        String alertMessageAsHex = Utils.HEX.encode(alert.getMessage().getBytes());
        try {
            ECKey.fromPublicOnly(HEX.decode(pubKeyAsHex)).verifyMessage(alertMessageAsHex, alert.getSignatureAsBase64());
            return true;
        } catch (SignatureException e) {
            log.warn("verifySignature failed");
            return false;
        }
    }
}
