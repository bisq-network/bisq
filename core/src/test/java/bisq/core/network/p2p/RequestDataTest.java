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

package bisq.core.network.p2p;

import bisq.core.account.witness.AccountAgeWitness;

import bisq.network.p2p.network.LocalhostNetworkNode;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.SequenceNumberMap;

import bisq.common.storage.Storage;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for migrating to and operating a multi-file file-based data store system
 */
public class RequestDataTest extends FileDatabaseTestUtils {

    /**
     * TEST CASE: test the optimized query creation
     *
     * USE CASE:
     * In order to save on data to be transmitted, we summarize data history by reporting
     * our bisq version as a "special key". The queried peers then know which data we
     * already have.
     *
     * RESULT
     * Test whether our client creates the correct query.
     */
    @Test
    public void query() throws IOException {
        // setup scenario
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(-1)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), object2);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object3);

        // simulate bisq startup
        P2PDataStorage DUT = new P2PDataStorage(new LocalhostNetworkNode(9999, null), null, loadDatabase(), new ProtectedDataStoreService(), null, new SequenceNumberStorageFake(), null, 0);
        Set<P2PDataStorage.ByteArray> result = DUT.buildPreliminaryGetDataRequest(0).getExcludedKeys().stream().map(bytes -> new P2PDataStorage.ByteArray(bytes)).collect(Collectors.toSet());

        // check result
        // - check total number of elements
        Assert.assertEquals(2, result.size());
        // - check keys
        Assert.assertFalse(result.contains(new P2PDataStorage.ByteArray(object1.getHash())));
        Assert.assertFalse(result.contains(new P2PDataStorage.ByteArray(object2.getHash())));
        Assert.assertTrue(result.contains(new P2PDataStorage.ByteArray(object3.getHash())));
        // - check special key
        Assert.assertTrue(result.contains(new P2PDataStorage.ByteArray(getSpecialKey(getVersion(0)).getHash())));
    }

    /**
     * TEST CASE: test the optimized query evaluation
     *
     * USE CASE:
     * In order to save on data to be transmitted, we summarize data history by reporting
     * our bisq version as a "special key". The queried peers then know which data we
     * already have.
     *
     * RESULT
     * Given the new query, see if another peer would respond correctly
     */
    @Test
    public void response() throws IOException {
        // setup scenario
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(-1)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), object2);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object3);

        // simulate bisq startup
        P2PDataStorage DUT = new P2PDataStorage(new LocalhostNetworkNode(9999, null), null, loadDatabase(), new ProtectedDataStoreService(), null, new SequenceNumberStorageFake(), null, 0);
        PreliminaryGetDataRequest query = new PreliminaryGetDataRequest(0, new HashSet<>(Arrays.asList(getSpecialKey(getVersion(0)).getHash())));
        Set<PersistableNetworkPayload> result = DUT.buildGetDataResponse(query, 100000, null, null, null).getPersistableNetworkPayloadSet();

        // check result
        // - check total number of elements
        Assert.assertEquals(1, result.size());
        // - check keys
        Assert.assertFalse(result.contains(object1));
        Assert.assertFalse(result.contains(object2));
        Assert.assertTrue(result.contains(object3));

        // alter query slightly
        query = new PreliminaryGetDataRequest(0, new HashSet<>(Arrays.asList(getSpecialKey(getVersion(-1)).getHash(), object3.getHash())));
        result = DUT.buildGetDataResponse(query, 100000, null, null, null).getPersistableNetworkPayloadSet();

        // check result
        // - check total number of elements
        Assert.assertEquals(1, result.size());
        // - check keys
        Assert.assertFalse(result.contains(object1));
        Assert.assertTrue(result.contains(object2));
        Assert.assertFalse(result.contains(object3));
    }


    /////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////// Utils /////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////

    public class SequenceNumberStorageFake extends Storage<SequenceNumberMap> {

        public SequenceNumberStorageFake() {
            super(new File("src/test/resources"), null, null);
        }

        @Override
        public void setNumMaxBackupFiles(int numMaxBackupFiles) {
        }
    }

    public static AccountAgeWitness getSpecialKey(String version) {
        byte[] result = new byte[20];
        Arrays.fill(result, (byte) 0);
        System.arraycopy(version.getBytes(), 0, result, 0, version.length());
        return new AccountAgeWitness(result, 0);
    }
}
