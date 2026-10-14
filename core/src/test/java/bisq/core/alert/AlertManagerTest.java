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
import bisq.common.util.ExtraDataMapValidator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Map<String, String> extraDataMap = Map.copyOf(alert.getExtraDataMap());

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(alert, 1)));

        assertEquals(extraDataMap, alert.getExtraDataMap());
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

    // An update alert with an invalid version is ignored, so it can never reach the version comparison at startup
    @ParameterizedTest
    @ValueSource(strings = {"", "1.10", "1.10.10.1", "1.x.10", "a.b.c", "1..10", "v1.10.10", "1.10.10-rc1",
            "+1.10.10", "1.10.-1", "1.10.10.", "99999999999.1.1"})
    void updateAlertWithInvalidVersionIsIgnored(String version) {
        Alert updateAlert = signedAlert("update", true, false, version);

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(updateAlert, 1)));

        assertFalse(alertManager.hasAnyAlert());
        assertNull(alertManager.alertMessageProperty().get());
    }

    @Test
    void preReleaseAlertWithInvalidVersionIsIgnored() {
        Alert preReleaseAlert = signedAlert("pre-release", false, true, "");

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(preReleaseAlert, 1)));

        assertFalse(alertManager.hasAnyAlert());
    }

    @Test
    void updateAlertWithInvalidVersionFromBootstrapDataIsIgnoredAtStartup() {
        Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> dataMap = new LinkedHashMap<>();
        dataMap.put(new P2PDataStorage.ByteArray(new byte[]{1}),
                protectedStorageEntry(signedAlert("update", true, false, ""), 1));
        when(p2PService.getDataMap()).thenReturn(dataMap);

        alertManager.onAllServicesInitialized();

        assertFalse(alertManager.hasAnyAlert());
        assertNull(alertManager.alertMessageProperty().get());
    }

    @Test
    void newerUpdateAlertWithInvalidVersionDoesNotReplaceTheCurrentAlert() {
        Alert currentAlert = signedAlert("current", true, false, "1.10.10");
        Alert invalidAlert = signedAlert("newer", true, false, "1.10");
        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(currentAlert, 1)));

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(invalidAlert, 2)));

        assertSame(currentAlert, alertManager.alertMessageProperty().get());
    }

    // Positive control: a valid version is accepted, and a normal alert needs no version
    @Test
    void updateAlertWithValidVersionAndNormalAlertWithoutVersionAreAccepted() {
        Alert updateAlert = signedAlert("update", true, false, "1.10.10");
        Alert normalAlert = signedAlert("notice", false, false, "");

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(updateAlert, 1)));
        assertSame(updateAlert, alertManager.alertMessageProperty().get());

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(normalAlert, 2)));
        assertSame(normalAlert, alertManager.alertMessageProperty().get());
    }

    // Positive control: an alert published by the real publishing path is accepted after a network round trip
    @Test
    void publishedAlertIsAcceptedAfterNetworkRoundTrip() throws InvalidProtocolBufferException {
        Alert received = roundTrip(signedAlert("update", true, false, "1.10.10"));

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(received, 1)));

        assertSame(received, alertManager.alertMessageProperty().get());
    }

    private static Stream<Arguments> changedFields() {
        return Stream.of(
                changedField("message", builder -> builder.setMessage("other")),
                changedField("version", builder -> builder.setVersion("99.0.0")),
                changedField("update flag removed", builder -> builder.setIsUpdateInfo(false)),
                changedField("pre-release flag added", builder -> builder.setIsPreReleaseInfo(true)),
                changedField("extra data added", builder -> builder.putExtraData("key", "value")),
                changedField("owner key", builder -> builder.setOwnerPubKeyBytes(
                        ByteString.copyFrom(Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic())))),
                changedField("message signature re-encoded",
                        builder -> builder.setSignatureAsBase64(reEncoded(builder.getSignatureAsBase64()))),
                changedField("alert signature removed",
                        builder -> builder.removeExtraData(AlertSignature.EXTRA_DATA_KEY)));
    }

    private static Arguments changedField(String name, UnaryOperator<protobuf.Alert.Builder> change) {
        return Arguments.of(Named.of(name, change));
    }

    // A modified client can change any field of a published alert
    @ParameterizedTest
    @MethodSource("changedFields")
    void publishedAlertWithAChangedFieldIsIgnored(UnaryOperator<protobuf.Alert.Builder> change)
            throws InvalidProtocolBufferException {
        Alert changed = changed(signedAlert("update", true, false, "1.10.10"), change);

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(changed, 1)));

        assertFalse(alertManager.hasAnyAlert());
    }

    @Test
    void normalAlertChangedIntoAnUpdateAlertIsIgnored() throws InvalidProtocolBufferException {
        Alert changed = changed(signedAlert("notice"), builder -> builder.setIsUpdateInfo(true).setVersion("99.0.0"));

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(changed, 1)));

        assertFalse(alertManager.hasAnyAlert());
    }

    @Test
    void alertSignatureOfAnotherAlertIsIgnored() throws InvalidProtocolBufferException {
        Alert otherAlert = signedAlert("update", true, false, "99.0.0");
        Alert changed = changed(signedAlert("update", true, false, "1.10.10"),
                builder -> builder.setVersion("99.0.0")
                        .putExtraData(AlertSignature.EXTRA_DATA_KEY, otherAlert.getAlertSignature()));

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(changed, 1)));

        assertFalse(alertManager.hasAnyAlert());
    }

    @Test
    void alertSignatureByAnotherKeyIsIgnored() throws InvalidProtocolBufferException {
        Alert changed = changed(signedAlert("update", true, false, "1.10.10"), builder -> builder.setVersion("99.0.0"));
        String signatureByAnotherKey = AlertSignature.sign(changed, new ECKey());
        Alert signedByAnotherKey = changed(changed,
                builder -> builder.putExtraData(AlertSignature.EXTRA_DATA_KEY, signatureByAnotherKey));

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(signedByAnotherKey, 1)));

        assertFalse(alertManager.hasAnyAlert());
    }

    @Test
    void changedCopyDoesNotReplaceTheCurrentAlert() throws InvalidProtocolBufferException {
        Alert published = roundTrip(signedAlert("update", true, false, "1.10.10"));
        Alert changed = changed(published, builder -> builder.setVersion("99.0.0"));
        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(published, 1)));

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(changed, 2)));

        assertSame(published, alertManager.alertMessageProperty().get());
    }

    // An alert published by an earlier version carries only the message signature
    @Test
    void alertWithOnlyTheMessageSignatureIsIgnored() {
        Alert alert = new Alert("notice", false, false, "");
        ECKey alertKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(DevEnv.getDEV_PRIVILEGE_PRIV_KEY())));
        String alertMessageAsHex = Utils.HEX.encode(alert.getMessage().getBytes(StandardCharsets.UTF_8));
        alert.setSigAndPubKey(LowRSigningKey.from(alertKey).signMessage(alertMessageAsHex),
                Sig.generateKeyPair().getPublic());

        hashMapChangedListener.onAdded(List.of(protectedStorageEntry(alert, 1)));

        assertFalse(alertManager.hasAnyAlert());
    }

    // Nodes of earlier versions verify the message signature and keep the extra data
    @Test
    void publishedAlertPassesTheMessageSignatureCheckOfEarlierVersions() throws InvalidProtocolBufferException {
        Alert received = roundTrip(signedAlert("update", true, false, "1.10.10"));

        String alertMessageAsHex = Utils.HEX.encode(received.getMessage().getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> ECKey.fromPublicOnly(HEX.decode(DevEnv.getDEV_PRIVILEGE_PUB_KEY()))
                .verifyMessage(alertMessageAsHex, received.getSignatureAsBase64()));
        assertEquals(received.getExtraDataMap(),
                ExtraDataMapValidator.getValidatedExtraDataMap(received.getExtraDataMap()));
        assertEquals(Map.of(AlertSignature.EXTRA_DATA_KEY, received.getAlertSignature()), received.getExtraDataMap());
    }

    @Test
    void developersAlertCarriesTheAlertSignature() {
        User user = mock(User.class);
        Alert alert = new Alert("notice", false, false, "");

        assertTrue(publisher(user).addAlertMessageIfKeyIsValid(alert, DevEnv.getDEV_PRIVILEGE_PRIV_KEY()));

        verify(user).setDevelopersAlert(alert);
        assertTrue(AlertSignature.verify(alert, DevEnv.getDEV_PRIVILEGE_PUB_KEY()));
    }

    private Alert signedAlert(String message) {
        return signedAlert(message, false, false, "");
    }

    // Signs the alert with the publishing path of AlertManager, as the developers do with the alert key
    private Alert signedAlert(String message, boolean isUpdateInfo, boolean isPreReleaseInfo, String version) {
        Alert alert = new Alert(message, isUpdateInfo, isPreReleaseInfo, version);
        assertTrue(publisher(mock(User.class)).addAlertMessageIfKeyIsValid(alert, DevEnv.getDEV_PRIVILEGE_PRIV_KEY()));
        return alert;
    }

    private static AlertManager publisher(User user) {
        KeyRing keyRing = mock(KeyRing.class);
        when(keyRing.getSignatureKeyPair()).thenReturn(Sig.generateKeyPair());
        return new AlertManager(mock(P2PService.class), keyRing, user, false, true);
    }

    private static Alert roundTrip(Alert alert) throws InvalidProtocolBufferException {
        byte[] bytes = alert.toProtoMessage().toByteArray();
        return Alert.fromProto(protobuf.StoragePayload.parseFrom(bytes).getAlert());
    }

    // The header byte of a Bitcoin message signature also encodes the key format; the other variant still verifies
    private static String reEncoded(String signatureAsBase64) {
        byte[] signature = Base64.getDecoder().decode(signatureAsBase64);
        signature[0] = (byte) (signature[0] >= 31 ? signature[0] - 4 : signature[0] + 4);
        return Base64.getEncoder().encodeToString(signature);
    }

    private static Alert changed(Alert alert, UnaryOperator<protobuf.Alert.Builder> change)
            throws InvalidProtocolBufferException {
        protobuf.Alert.Builder builder = alert.toProtoMessage().getAlert().toBuilder();
        byte[] bytes = protobuf.StoragePayload.newBuilder().setAlert(change.apply(builder)).build().toByteArray();
        return Alert.fromProto(protobuf.StoragePayload.parseFrom(bytes).getAlert());
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
