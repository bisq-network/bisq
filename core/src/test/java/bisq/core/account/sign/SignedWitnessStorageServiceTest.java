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

package bisq.core.account.sign;

import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.crypto.Sig;
import bisq.common.persistence.PersistenceManager;

import java.security.KeyPair;

import java.nio.file.Path;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static bisq.core.account.sign.SignedWitness.VerificationMethod.TRADE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A witness with an empty field was stored before the store checked its fields. Such a stored witness is removed when
 * the store is read, so that the node does not serve it to peers in the initial data.
 */
class SignedWitnessStorageServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void readingTheStoreRemovesAStoredMalformedWitness(@TempDir Path storageDir) throws Exception {
        KeyPair keyPair = Sig.generateKeyPair();
        byte[] key = Sig.getPublicKeyBytes(keyPair.getPublic());
        byte[] accountHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{1});
        SignedWitness valid = new SignedWitness(TRADE, accountHash, Sig.sign(keyPair.getPrivate(), accountHash), key,
                key, 1, 1000);
        SignedWitness emptyOwner = new SignedWitness(TRADE, accountHash, Sig.sign(keyPair.getPrivate(), accountHash),
                key, new byte[0], 1, 1000);
        SignedWitnessStore persisted = new SignedWitnessStore();
        persisted.getMap().put(new P2PDataStorage.ByteArray(valid.getHash()), valid);
        persisted.getMap().put(new P2PDataStorage.ByteArray(emptyOwner.getHash()), emptyOwner);
        PersistenceManager<SignedWitnessStore> persistenceManager = mock(PersistenceManager.class);
        doAnswer(invocation -> {
            ((Consumer<SignedWitnessStore>) invocation.getArgument(1)).accept(persisted);
            return null;
        }).when(persistenceManager).readPersisted(anyString(), any(), any());
        SignedWitnessStorageService service = new SignedWitnessStorageService(storageDir.toFile(), persistenceManager);
        AtomicBoolean isComplete = new AtomicBoolean();

        // A post fix without a resource file, so that the store is the persisted one
        service.readFromResources("_TEST", () -> isComplete.set(true));

        assertTrue(isComplete.get());
        assertEquals(1, service.getMap().size());
        assertTrue(service.getMap().containsKey(new P2PDataStorage.ByteArray(valid.getHash())));
        assertFalse(service.getMap().containsKey(new P2PDataStorage.ByteArray(emptyOwner.getHash())));
        verify(persistenceManager).requestPersistence();
    }
}
