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

import bisq.common.app.Version;
import bisq.common.storage.Storage;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the "reduced" initial query size feature. See <a href="https://github.com/bisq-network/projects/issues/25">
 *     project description</a> on github.
 */
public class RequestDataTest extends FileDatabaseTestUtils {

    /**
     * TEST CASE: test the optimized query creation<br><br>
     *
     * USE CASE:
     * In order to save on data to be transmitted, we summarize data history by reporting
     * our bisq version as a "special key". The queried peers then know which data we
     * already have.<br><br>
     *
     * RESULT:
     * Test whether our client creates the correct query.
     */
    @Test
    public void query() throws IOException {
        // setup scenario
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(-1)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), object2);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object3);

        // create a PreliminaryGetDataRequest as a Device Under Test
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
     * TEST CASE: test the optimized query evaluation<br><br>
     *
     * USE CASE:
     * In order to save on data to be transmitted, we summarize data history by reporting
     * our bisq version as a "special key". The queried peers then know which data we
     * already have.<br><br>
     *
     * RESULT:
     * Given the new query, see if another peer would respond correctly
     */
    @Test
    public void response() throws IOException {
        // setup scenario
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(-1)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), object2);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object3);

        // craft a PreliminaryGetDataRequest as a query to get a GetDataResponse
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

    /**
     * TEST CASE: what happens if a faulty key arrives<br><br>
     *
     * USE CASE:
     * An Attacker tries to hijack the protocol by sending a incorrect "special key". The
     * system should be resilient against that and thus, recover and fall back to a known
     * state.<br><br>
     *
     * RESULT:
     * Although it would be nice to have some sort of detection, it is hard to do right.
     * So we just stick to what happened until now, namely, we ignore the special key
     * and let other size limits do the work.<br><br>
     *
     * However, we add an additional test to ensure Bisq version follows a strict pattern.
     */
    @Test
    public void faultySpecialKey() throws IOException {
        // setup scenario
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object2);

        // craft a PreliminaryGetDataRequest as a query to get a GetDataResponse
        P2PDataStorage DUT = new P2PDataStorage(new LocalhostNetworkNode(9999, null), null, loadDatabase(), new ProtectedDataStoreService(), null, new SequenceNumberStorageFake(), null, 0);

        // check results
        List<String> faultyKeys = Arrays.asList("1.3.13", "1.13.3", "13.3.3", "a.3.3", "13.a.3", "ä.3.3", Version.VERSION.replace(".", "_"), Version.VERSION.replace(".", ","));
        faultyKeys.forEach(s -> faultySpecialKeyHelper(DUT, s));
    }

    private void faultySpecialKeyHelper(P2PDataStorage DUT, String key) {
        PreliminaryGetDataRequest query = new PreliminaryGetDataRequest(0, new HashSet<>(Arrays.asList(getSpecialKey(key).getHash())));

        Set<PersistableNetworkPayload> result = DUT.buildGetDataResponse(query, 100000, null, null, null).getPersistableNetworkPayloadSet();
        Assert.assertEquals(key + " got accepted", 2, result.size());
        Assert.assertTrue(key + " got accepted", result.contains(object1));
        Assert.assertTrue(key + " got accepted", result.contains(object2));
    }

    /**
     * TEST CASE: make sure Bisq follows a strict pattern in release versioning.<br><br>
     *
     * USE CASE:
     * Bringing in the timely element we need to make sure Bisq's release versioning stays
     * true to the pattern it follows now.<br><br>
     *
     * RESULT:
     * If the pattern does not match, we fire!
     */
    @Test
    public void testBisqVersionFormat() {
        Assert.assertTrue("Bisq Version does not match formatting! x.y.z vs. " + Version.VERSION, Version.VERSION.matches("^[0-9]\\.[0-9]\\.[0-9]$"));
    }

    /**
     * TEST CASE: test what happens if an incoming "special key" is a future bisq version<br><br>
     *
     * USE CASE:
     * A Bisq client Alice of version x asks a Bisq client Bob of version x-1 for data.
     * The "special" key incoming to Bob describes its future. A real-world example is an
     * outdated seednode getting asked by an up-to-date client.<br><br>
     *
     * RESULT:
     * send anything new since the seednode's newest data bucket - ie. all live data. This
     * will result in duplicates arriving at the client but
     * <ul><li>we took care of that already</li>
     * <li>is a sane way of reacting to a newer client</li></ul>
     */
    @Test
    public void futureSpecialKey() throws IOException {
        // setup scenario
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(-1)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object2, object3);

        // craft a PreliminaryGetDataRequest as a query to get a GetDataResponse
        P2PDataStorage DUT = new P2PDataStorage(new LocalhostNetworkNode(9999, null), null, loadDatabase(), new ProtectedDataStoreService(), null, new SequenceNumberStorageFake(), null, 0);
        PreliminaryGetDataRequest query = new PreliminaryGetDataRequest(0, new HashSet<>(Arrays.asList(getSpecialKey(getVersion(0)).getHash())));
        Set<PersistableNetworkPayload> result = DUT.buildGetDataResponse(query, 100000, null, null, null).getPersistableNetworkPayloadSet();

        // check result
        // - check total number of elements
        Assert.assertEquals(2, result.size());
        // - check keys
        Assert.assertFalse(result.contains(object1));
        Assert.assertTrue(result.contains(object2));
        Assert.assertTrue(result.contains(object3));
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
