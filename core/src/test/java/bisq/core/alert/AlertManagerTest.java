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
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;

import com.google.common.base.Charsets;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AlertManagerTest {
    private P2PService p2PService;
    private AlertManager alertManager;
    private HashMapChangedListener hashMapChangedListener;

    @BeforeEach
    void setUp() {
        p2PService = mock(P2PService.class);
        when(p2PService.getDataMap()).thenReturn(Map.of());

        alertManager = new AlertManager(p2PService, mock(KeyRing.class), mock(User.class), false, true);

        ArgumentCaptor<HashMapChangedListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(HashMapChangedListener.class);
        verify(p2PService).addHashSetChangedListener(listenerArgumentCaptor.capture());
        hashMapChangedListener = listenerArgumentCaptor.getValue();
    }

    @Test
    void onRemovedPrunesTrackedAlerts() {
        Alert alert = signedAlert("alert");
        ProtectedStorageEntry protectedStorageEntry = protectedStorageEntry(alert, 1);

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry));
        assertTrue(alertManager.hasAnyAlert());
        assertSame(alert, alertManager.alertMessageProperty().get());

        hashMapChangedListener.onRemoved(List.of(protectedStorageEntry));

        assertFalse(alertManager.hasAnyAlert());
        assertNull(alertManager.alertMessageProperty().get());
    }

    @Test
    void removeAllAlertsUsesSnapshotWhenRemoveCallbacksPruneTrackedAlerts() {
        Alert firstAlert = signedAlert("first alert");
        Alert secondAlert = signedAlert("second alert");
        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(firstAlert, 1), protectedStorageEntry(secondAlert, 2)));
        doAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            hashMapChangedListener.onRemoved(List.of(protectedStorageEntry(alert)));
            return true;
        }).when(p2PService).removeData(any(Alert.class));

        assertTrue(alertManager.removeAllAlerts(DevEnv.getDEV_PRIVILEGE_PRIV_KEY()));

        assertFalse(alertManager.hasAnyAlert());
        verify(p2PService, times(2)).removeData(any(Alert.class));
    }

    @Test
    void onAllServicesInitializedHydratesExistingAlertsFromP2PMap() {
        Alert alert = signedAlert("persisted alert");
        ProtectedStorageEntry protectedStorageEntry = protectedStorageEntry(alert, 1);
        when(p2PService.getDataMap()).thenReturn(Map.of(
                new P2PDataStorage.ByteArray(new byte[]{1}),
                protectedStorageEntry
        ));

        alertManager.onAllServicesInitialized();

        assertTrue(alertManager.hasAnyAlert());
        assertSame(alert, alertManager.alertMessageProperty().get());
    }

    @Test
    void olderAlertDoesNotReplaceNewerAlert() {
        Alert newerAlert = signedAlert("newer alert");
        Alert olderAlert = signedAlert("older alert");

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(newerAlert, 2)));
        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(olderAlert, 1)));

        assertSame(newerAlert, alertManager.alertMessageProperty().get());
    }

    @Test
    void onAllServicesInitializedSetsNewestAlertFromUnsortedP2PMap() {
        Alert newerAlert = signedAlert("newer persisted alert");
        Alert olderAlert = signedAlert("older persisted alert");
        Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> dataMap = new LinkedHashMap<>();
        dataMap.put(new P2PDataStorage.ByteArray(new byte[]{1}), protectedStorageEntry(newerAlert, 2));
        dataMap.put(new P2PDataStorage.ByteArray(new byte[]{2}), protectedStorageEntry(olderAlert, 1));
        when(p2PService.getDataMap()).thenReturn(dataMap);

        alertManager.onAllServicesInitialized();

        assertSame(newerAlert, alertManager.alertMessageProperty().get());
    }

    @Test
    void removingNewestAlertSetsNextNewestAlert() {
        Alert olderAlert = signedAlert("older alert");
        Alert newerAlert = signedAlert("newer alert");
        ProtectedStorageEntry olderEntry = protectedStorageEntry(olderAlert, 1);
        ProtectedStorageEntry newerEntry = protectedStorageEntry(newerAlert, 2);
        hashMapChangedListener.onAdded(List.of(olderEntry, newerEntry));

        hashMapChangedListener.onRemoved(List.of(newerEntry));

        assertSame(olderAlert, alertManager.alertMessageProperty().get());
    }

    @Test
    void trackingCreationTimeStampDoesNotMutateAlertExtraDataMap() {
        Alert alert = signedAlert("alert");

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(alert, 1)));

        assertNull(alert.getExtraDataMap());
    }

    @Test
    void unsignedAlertToStringIsNullSafe() {
        Alert unsignedAlert = new Alert("message", false, false, "");

        assertDoesNotThrow(unsignedAlert::toString);
    }

    @Test
    void malformedAlertDoesNotBreakListener() {
        Alert malformedAlert = new Alert(null, false, false, "");
        ProtectedStorageEntry protectedStorageEntry = protectedStorageEntry(malformedAlert);

        assertDoesNotThrow(() -> hashMapChangedListener.onAdded(List.of(protectedStorageEntry)));
        assertFalse(alertManager.hasAnyAlert());
    }

    private Alert signedAlert(String message) {
        Alert alert = new Alert(message, false, false, "");
        ECKey signingKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(DevEnv.getDEV_PRIVILEGE_PRIV_KEY())));
        String alertMessageAsHex = Utils.HEX.encode(message.getBytes(Charsets.UTF_8));
        String signatureAsBase64 = LowRSigningKey.from(signingKey).signMessage(alertMessageAsHex);
        alert.setSigAndPubKey(signatureAsBase64, Sig.generateKeyPair().getPublic());
        return alert;
    }

    private ProtectedStorageEntry protectedStorageEntry(Alert alert) {
        return protectedStorageEntry(alert, 0);
    }

    private ProtectedStorageEntry protectedStorageEntry(Alert alert, long creationTimeStamp) {
        ProtectedStorageEntry protectedStorageEntry = mock(ProtectedStorageEntry.class);
        when(protectedStorageEntry.getProtectedStoragePayload()).thenReturn(alert);
        when(protectedStorageEntry.getCreationTimeStamp()).thenReturn(creationTimeStamp);
        return protectedStorageEntry;
    }
}
