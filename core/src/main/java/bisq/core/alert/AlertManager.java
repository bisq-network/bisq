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

package bisq.core.alert;

import bisq.core.crypto.LowRSigningKey;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.Charsets;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bitcoinj.core.Utils.HEX;

public class AlertManager {
    private static final Logger log = LoggerFactory.getLogger(AlertManager.class);

    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final User user;
    private final ObjectProperty<Alert> alertMessageProperty = new SimpleObjectProperty<>();
    private final Map<Alert, Long> addedAlerts = new HashMap<>();
    private final boolean ignoreDevMsg;
    private long alertMessageCreationTimeStamp = Long.MIN_VALUE;

    // Pub key for developer global alert message
    private final String pubKeyAsHex;
    private ECKey alertSigningKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization

    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AlertManager(P2PService p2PService,
                        KeyRing keyRing,
                        User user,
                        @Named(Config.IGNORE_DEV_MSG) boolean ignoreDevMsg,
                        @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.user = user;

        if (!ignoreDevMsg) {
            p2PService.addHashSetChangedListener(new HashMapChangedListener() {
                @Override
                public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                    protectedStorageEntries.forEach(protectedStorageEntry -> {
                        ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
                        if (protectedStoragePayload instanceof Alert alert) {
                            onAlertAdded(protectedStorageEntry);
                        }
                    });
                }

                @Override
                public void onRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                    protectedStorageEntries.forEach(protectedStorageEntry -> {
                        ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
                        if (protectedStoragePayload instanceof Alert alert) {
                            onAlertRemoved(alert);
                        }
                    });
                }
            });
        }
        this.ignoreDevMsg = ignoreDevMsg;
        pubKeyAsHex = useDevPrivilegeKeys ?
                DevEnv.getDEV_PRIVILEGE_PUB_KEY() :
                "036d8a1dfcb406886037d2381da006358722823e1940acc2598c844bbc0fd1026f";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API

    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyObjectProperty<Alert> alertMessageProperty() {
        return alertMessageProperty;
    }

    public void onAllServicesInitialized() {
        if (ignoreDevMsg) {
            return;
        }


        p2PService.getDataMap().values().stream()
                .filter(entry -> entry.getProtectedStoragePayload() instanceof Alert)
                .sorted(Comparator.comparingLong(ProtectedStorageEntry::getCreationTimeStamp))
                .forEach(this::onAlertAdded);
    }

    public boolean addAlertMessageIfKeyIsValid(Alert alert, String privKeyString) {
        // if there is a previous message we remove that first
        if (user.getDevelopersAlert() != null)
            removeDeveloperAlert(privKeyString);

        boolean isKeyValid = isKeyValid(privKeyString);
        if (isKeyValid) {
            signAndAddSignatureToAlertMessage(alert);
            user.setDevelopersAlert(alert);
            boolean result = p2PService.addProtectedStorageEntry(alert);
            if (result) {
                log.trace("Add alertMessage to network was successful. AlertMessage={}", alert);
            }

        }
        return isKeyValid;
    }

    public boolean hasDevelopersAlert() {
        return user.getDevelopersAlert() != null;
    }

    public boolean hasAnyAlert() {
        return !addedAlerts.isEmpty() || hasDevelopersAlert();
    }

    public boolean removeDeveloperAlert(String privKeyString) {
        if (!isKeyValid(privKeyString)) {
            return false;
        }

        Alert developersAlert = user.getDevelopersAlert();
        if (developersAlert != null) {
            user.setDevelopersAlert(null);
            removeAlert(developersAlert);
            return true;
        }

        return false;
    }

    public boolean removeAllAlerts(String privKeyString) {
        if (!isKeyValid(privKeyString)) {
            return false;
        }

        Set<Alert> alertsToRemove = new HashSet<>(addedAlerts.keySet());
        Alert developersAlert = user.getDevelopersAlert();
        if (developersAlert != null) {
            user.setDevelopersAlert(null);
            alertsToRemove.add(developersAlert);
        }
        alertsToRemove.forEach(this::removeAlert);
        return true;
    }

    private void removeAlert(Alert alert) {
        if (alert != null) {
            if (p2PService.removeData(alert)) {
                log.info("Remove alert from network was successful. Alert={}", alert);
            } else {
                log.warn("Removing alert failed. alert={}", alert);
            }
        }
    }

    private void onAlertAdded(ProtectedStorageEntry protectedStorageEntry) {
        ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof Alert alert) {
            if (verifySignature(alert)) {
                long creationTimeStamp = protectedStorageEntry.getCreationTimeStamp();
                log.info("Alert added: {}, creationTimeStamp={}", alert, creationTimeStamp);
                Long previousCreationTimeStamp = addedAlerts.get(alert);
                if (previousCreationTimeStamp == null || creationTimeStamp > previousCreationTimeStamp) {
                    addedAlerts.put(alert, creationTimeStamp);
                }

                if (creationTimeStamp > alertMessageCreationTimeStamp) {
                    setAlertMessage(alert, creationTimeStamp);
                }
            } else {
                log.warn("Signature verification failed at adding alert {}", alert);
            }
        }
    }

    private void onAlertRemoved(Alert alert) {
        if (verifySignature(alert)) {
            log.info("Alert removed: {}", alert);
            addedAlerts.remove(alert);
            if (alert.equals(alertMessageProperty.get())) {
                setNewestAlertMessage();
            }
        } else {
            log.warn("Signature verification failed at removing alert {}", alert);
        }
    }

    private void setNewestAlertMessage() {
        addedAlerts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresentOrElse(
                        entry -> setAlertMessage(entry.getKey(), entry.getValue()),
                        () -> setAlertMessage(null, Long.MIN_VALUE));
    }

    private void setAlertMessage(Alert alert, long creationTimeStamp) {
        alertMessageProperty.set(alert);
        alertMessageCreationTimeStamp = creationTimeStamp;
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
        String alertMessageAsHex = Utils.HEX.encode(alert.getMessage().getBytes(StandardCharsets.UTF_8));
        String signatureAsBase64 = LowRSigningKey.from(alertSigningKey).signMessage(alertMessageAsHex);
        alert.setSigAndPubKey(signatureAsBase64, keyRing.getSignatureKeyPair().getPublic());
    }

    private boolean verifySignature(Alert alert) {
        try {
            String alertMessageAsHex = Utils.HEX.encode(alert.getMessage().getBytes(StandardCharsets.UTF_8));
            ECKey.fromPublicOnly(HEX.decode(pubKeyAsHex)).verifyMessage(alertMessageAsHex, alert.getSignatureAsBase64());
            return true;
        } catch (Exception e) {
            log.warn("verifySignature failed. alert={}", alert);
            return false;
        }
    }
}
