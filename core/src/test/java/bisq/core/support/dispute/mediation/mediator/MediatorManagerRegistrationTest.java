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

package bisq.core.support.dispute.mediation.mediator;

import bisq.core.filter.FilterManager;
import bisq.core.filter.FilterPolicyService;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.app.DevEnv;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import java.security.KeyPair;
import java.security.PublicKey;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The registry of mediators accepts a mediator only with a listed registration key and a valid registration
 * signature. A mediator with a malformed key or signature, which anyone can publish, is rejected without stopping
 * the update of the registry.
 */
class MediatorManagerRegistrationTest {
    private static final ECKey REGISTRATION_KEY =
            ECKey.fromPrivate(new BigInteger(1, Utils.HEX.decode(DevEnv.getDEV_PRIVILEGE_PRIV_KEY())));
    private static final NodeAddress MEDIATOR_ADDRESS = new NodeAddress("mediator.onion", 9999);
    private static final NodeAddress OTHER_ADDRESS = new NodeAddress("other.onion", 9999);

    private final P2PService p2PService = mock(P2PService.class);
    private final MediatorService mediatorService = new MediatorService(p2PService, mock(FilterPolicyService.class));
    // Trade validation reads the accepted mediators from the user
    private final User user = new User();
    private final MediatorManager manager =
            new MediatorManager(null, mediatorService, user, mock(FilterManager.class), true);

    @Test
    void aRegisteredMediatorIsAccepted() {
        Mediator mediator = registeredMediator(MEDIATOR_ADDRESS);
        givenMediators(mediator);

        manager.updateMap();

        assertRegistered(mediator);
    }

    // Anyone can publish a mediator with an unlisted key. An empty key makes bitcoinj throw when it is decoded.
    @Test
    void anUnlistedMediatorWithAMalformedKeyIsRejected() {
        Mediator registered = registeredMediator(MEDIATOR_ADDRESS);
        givenMediators(registered, mediator(OTHER_ADDRESS, new byte[0], "signature"));

        manager.updateMap();

        assertRegistered(registered);
    }

    // The bytes of an unlisted key are never parsed
    @Test
    void theSignatureOfAnUnlistedMediatorIsNotChecked() {
        List<byte[]> checkedKeys = new ArrayList<>();
        MediatorManager countingManager = new MediatorManager(null, mediatorService, user,
                mock(FilterManager.class), true) {
            @Override
            protected boolean verifySignature(PublicKey storageSignaturePubKey,
                                              byte[] registrationPubKey,
                                              String signature) {
                checkedKeys.add(registrationPubKey);
                return super.verifySignature(storageSignaturePubKey, registrationPubKey, signature);
            }
        };
        givenMediators(registeredMediator(MEDIATOR_ADDRESS), mediator(OTHER_ADDRESS, new byte[]{5, 1}, "signature"));

        countingManager.updateMap();

        assertEquals(1, checkedKeys.size());
        assertArrayEquals(REGISTRATION_KEY.getPubKey(), checkedKeys.get(0));
    }

    @Test
    void aListedKeyWithAMalformedSignatureIsRejected() {
        Mediator registered = registeredMediator(MEDIATOR_ADDRESS);
        givenMediators(registered, mediator(OTHER_ADDRESS, REGISTRATION_KEY.getPubKey(), malformedSignature()));

        manager.updateMap();

        assertRegistered(registered);
    }

    @Test
    void aListedKeyWithASignatureForAnotherMediatorIsRejected() {
        Mediator registered = registeredMediator(MEDIATOR_ADDRESS);
        // The signature covers the storage key of another mediator
        givenMediators(registered, mediator(OTHER_ADDRESS, REGISTRATION_KEY.getPubKey(), signatureFor(pubKeyRing())));

        manager.updateMap();

        assertRegistered(registered);
    }

    // Anyone can publish a mediator with the address of a registered one. The attacker chooses free fields, such as the
    // registration date, until its mediator comes first in the set of published ones.
    @Test
    void anUnregisteredMediatorWithTheAddressOfARegisteredOneDoesNotHideIt() {
        Mediator registered = registeredMediator(MEDIATOR_ADDRESS);
        Mediator shadow = comingFirstBefore(registered,
                date -> mediator(MEDIATOR_ADDRESS, pubKeyRing(), new byte[]{2, 1}, "signature", date));
        givenMediators(shadow, registered);

        manager.updateMap();

        assertRegistered(registered);
    }

    // The registration signature covers the storage key of the mediator, so a copy with another storage key is invalid
    @Test
    void aCopiedRegistrationWithTheAddressOfARegisteredMediatorDoesNotHideIt() {
        Mediator registered = registeredMediator(MEDIATOR_ADDRESS);
        Mediator copy = comingFirstBefore(registered, date -> mediator(MEDIATOR_ADDRESS, pubKeyRing(),
                registered.getRegistrationPubKey(), registered.getRegistrationSignature(), date));
        givenMediators(copy, registered);

        manager.updateMap();

        assertRegistered(registered);
    }

    @Test
    void ofTwoRegistrationsOfAnAddressTheLaterOneCounts() {
        Mediator later = registeredMediator(MEDIATOR_ADDRESS, 2);
        Mediator earlier = comingFirstBefore(later, date -> registeredMediator(MEDIATOR_ADDRESS, 1));
        givenMediators(earlier, later);

        manager.updateMap();

        assertRegistered(later);
    }

    // An update that fails leaves the registry as it was. A mock stands in for a mediator whose check throws.
    @Test
    void theRegistryIsReplacedOnlyAfterAllMediatorsWereChecked() {
        Mediator registered = registeredMediator(MEDIATOR_ADDRESS);
        givenMediators(registered);
        manager.updateMap();
        Mediator failing = mock(Mediator.class);
        when(failing.getNodeAddress()).thenReturn(OTHER_ADDRESS);
        when(failing.getRegistrationPubKey()).thenThrow(new IllegalStateException("broken"));
        givenMediators(registered, failing);

        assertThrows(IllegalStateException.class, manager::updateMap);

        assertRegistered(registered);
    }

    private void assertRegistered(Mediator mediator) {
        assertEquals(Map.of(MEDIATOR_ADDRESS, mediator), manager.getObservableMap());
        assertEquals(List.of(mediator), user.getAcceptedMediators());
    }

    // As the P2P network stores the published mediators
    private void givenMediators(Mediator... mediators) {
        Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> dataMap = new LinkedHashMap<>();
        for (Mediator mediator : mediators) {
            ProtectedStorageEntry entry = mock(ProtectedStorageEntry.class);
            when(entry.getProtectedStoragePayload()).thenReturn(mediator);
            dataMap.put(new P2PDataStorage.ByteArray(new byte[]{(byte) dataMap.size()}), entry);
        }
        when(p2PService.getDataMap()).thenReturn(dataMap);
    }

    private static Mediator registeredMediator(NodeAddress nodeAddress) {
        return registeredMediator(nodeAddress, 0);
    }

    private static Mediator registeredMediator(NodeAddress nodeAddress, long registrationDate) {
        PubKeyRing pubKeyRing = pubKeyRing();
        return mediator(nodeAddress, pubKeyRing, REGISTRATION_KEY.getPubKey(), signatureFor(pubKeyRing),
                registrationDate);
    }

    // MediatorService collects the published mediators into a HashSet in the order of the data map, and the order of
    // the set follows their hash. givenMediators puts the returned mediator first.
    private static Mediator comingFirstBefore(Mediator other, LongFunction<Mediator> mediatorWithDate) {
        for (long date = 1; date < 1000; date++) {
            Mediator mediator = mediatorWithDate.apply(date);
            Set<Mediator> published = new HashSet<>();
            published.add(mediator);
            published.add(other);
            if (published.iterator().next() == mediator) {
                return mediator;
            }
        }
        throw new IllegalStateException("No mediator found that comes first");
    }

    private static Mediator mediator(NodeAddress nodeAddress, byte[] registrationPubKey, String signature) {
        return mediator(nodeAddress, pubKeyRing(), registrationPubKey, signature);
    }

    private static Mediator mediator(NodeAddress nodeAddress,
                                     PubKeyRing pubKeyRing,
                                     byte[] registrationPubKey,
                                     String signature) {
        return mediator(nodeAddress, pubKeyRing, registrationPubKey, signature, 0);
    }

    private static Mediator mediator(NodeAddress nodeAddress,
                                     PubKeyRing pubKeyRing,
                                     byte[] registrationPubKey,
                                     String signature,
                                     long registrationDate) {
        return new Mediator(nodeAddress, pubKeyRing, List.of("en"), registrationDate, registrationPubKey, signature,
                null, null);
    }

    // As DisputeAgentManager.signStorageSignaturePubKey signs the storage key of the agent
    private static String signatureFor(PubKeyRing pubKeyRing) {
        return REGISTRATION_KEY.signMessage(Utils.HEX.encode(pubKeyRing.getSignaturePubKey().getEncoded()));
    }

    // A compact signature with r = 0, which bitcoinj cannot recover a key from
    private static String malformedSignature() {
        byte[] signature = new byte[65];
        signature[0] = 27;
        signature[64] = 1;
        return Base64.getEncoder().encodeToString(signature);
    }

    private static PubKeyRing pubKeyRing() {
        KeyPair signatureKeyPair = Sig.generateKeyPair();
        return new PubKeyRing(signatureKeyPair.getPublic(), Encryption.generateKeyPair().getPublic());
    }
}
