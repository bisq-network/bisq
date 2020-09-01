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

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for migrating to and operating a multi-file file-based data store system
 *
 * TODO these tests are bound to be changed once Bisq migrates to a real database backend
 */
public class FileDatabaseTest extends FileDatabaseTestUtils {

    /**
     * TEST CASE: check if test fixture databases are in place and correct<br><br>
     *
     * This does not test any business logic, just makes sure the test setup is correct.
     */
    @Test
    public void checkTestFixtures() throws Exception {
        checkTestFixturesHelper(object1);
        checkTestFixturesHelper(object1, object2);
        checkTestFixturesHelper(object2);
        checkTestFixturesHelper(object2, object3);
        checkTestFixturesHelper(object3);
    }

    private void checkTestFixturesHelper(AccountAgeWitness... objects) throws Exception {
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), objects);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), objects);
        AppendOnlyDataStoreService DUT = loadDatabase();
        Assert.assertEquals(objects.length, DUT.getMap().size());
        Arrays.stream(objects).forEach(object -> {
            Assert.assertTrue(DUT.getMap().containsValue(object));
        });
    }

    /**
     * TEST CASE: test migration scenario from old database file model to new one<br><br>
     *
     * USE CASE:
     * We migrate from just having one working-dir database file to having multiple. In
     * detail, the user starts with having one database file in her working directory and
     * one in her resources. After the update, there is still one database in her resources
     * but it is labelled differently.<br><br>
     *
     * RESULT:
     * There are 2 data stores in her working directory, one holding the live database,
     * the other one being the exact and readonly copy of the database in resources. Plus,
     * the 2 data stores do not share any set of objects.
     */
    @Test
    public void migrationScenario() throws Exception {
        // setup scenario
        // - create one data store in working directory
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object1, object2);
        // - create one data store in resources with new naming scheme
        createDatabase(createFile(true, "AccountAgeWitnessStore_" + getVersion(0) + "_TEST"), object1);

        // simulate bisq startup
        final AppendOnlyDataStoreService DUT = loadDatabase();

        // check result
        // - check total number of elements
        Assert.assertEquals(2, DUT.getMap().size());
        // - are there 2 data stores in working-dir
        Assert.assertEquals(2, storageDir.list((dir, name) -> name.startsWith("AccountAgeWitnessStore") && !name.endsWith("_TEST")).length);
        // - do the 2 data stores share objects
        Assert.assertEquals(1, DUT.getMap("since " + getVersion(0)).size());
        Assert.assertEquals(1, DUT.getMap().size() - DUT.getMap("since " + getVersion(0)).size());
    }

    /**
     * TEST CASE: test migration scenario from old database file model to new one but the
     * user skipped some releases before upgrading<br><br>
     *
     * USE CASE:
     * We migrate from just having one working-dir database file to having multiple. In
     * detail, the user starts with having one database file in her working directory and
     * multiple data store files in her resources. This can happen if the user does not
     * upgrade at the first possible moment. After the update, however, there is all of the
     * resource files copied to her working directory plus the live data store.<br><br>
     *
     * RESULT:
     * There are 2 data stores in her working directory, one holding the live database,
     * the other one being the exact and readonly copy of the database in resources. Plus,
     * the 2 data stores do not share any set of objects.
     */
    @Test
    public void migrationScenario2() throws Exception {
        // setup scenario
        // - create one data store in working directory
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object1, object2);
        // - create one data store in resources with new naming scheme
        createDatabase(createFile(true, "AccountAgeWitnessStore_" + getVersion(-1) + "_TEST"), object1);
        createDatabase(createFile(true, "AccountAgeWitnessStore_" + getVersion(0) + "_TEST"), object2);

        // simulate bisq startup
        final AppendOnlyDataStoreService DUT = loadDatabase();

        // check result
        // - check total number of elements
        Assert.assertEquals(2, DUT.getMap().size());
        // - are there 2 data stores in working-dir
        Assert.assertEquals(3, storageDir.list((dir, name) -> name.startsWith("AccountAgeWitnessStore") && !name.endsWith("_TEST")).length);
        // - do the 2 data stores share objects
        Assert.assertEquals(0, DUT.getMap("since " + getVersion(0)).size());
        Assert.assertEquals(1, DUT.getMap("since " + getVersion(-1)).size());
    }

    /**
     * TEST CASE: test Bisq software update scenario<br><br>
     *
     * USE CASE:
     * Given a newly released Bisq version x, a new differential database is added to
     * the resources. The new database has to be copied to the working directory and the
     * live database has to be stripped of the entries found in the new database.<br><br>
     *
     * RESULT:
     * There are n + 1 data stores in the users working directory, one holding the live
     * database, the other 2 being the exact and readonly copy of the data stores in
     * resources. Plus, the data stores in the working dir do not share any set of objects.
     */
    @Test
    public void updateScenario() throws Exception {
        // setup scenario
        // - create two data stores in resources
        createDatabase(createFile(true, "AccountAgeWitnessStore_" + getVersion(-1) + "_TEST"), object1);
        createDatabase(createFile(true, "AccountAgeWitnessStore_" + getVersion(0) + "_TEST"), object2);
        // - create two data stores in work dir
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(-1)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object2, object3);

        // simulate bisq startup
        AppendOnlyDataStoreService DUT = loadDatabase();

        // check result
        // - check total number of elements
        Assert.assertEquals(3, DUT.getMap().size());
        // - are there 2 data stores in working-dir
        Assert.assertEquals(3, storageDir.list((dir, name) -> name.startsWith("AccountAgeWitnessStore") && !name.endsWith("_TEST")).length);
        // - do the 2 data stores share objects
        Assert.assertEquals(1, DUT.getMap("since " + getVersion(0)).size());
        Assert.assertEquals(2, DUT.getMap().size() - DUT.getMap("since " + getVersion(0)).size());
    }

    /**
     * TEST CASE: test clean install of Bisq software app<br><br>
     *
     * USE CASE:
     * A user has a fresh install of Bisq. Ie. there are two database files in resources
     * and none in the working directory.<br><br>
     *
     * RESULT:
     * After startup, there should be 3 data stores in the working directory.
     */
    @Test
    public void freshInstallScenario() throws Exception {
        // setup scenario
        // - create two data stores in resources
        createDatabase(createFile(true, "AccountAgeWitnessStore_" + getVersion(-1) + "_TEST"), object1);
        createDatabase(createFile(true, "AccountAgeWitnessStore_" + getVersion(0) + "_TEST"), object2);

        // simulate bisq startup
        AppendOnlyDataStoreService DUT = loadDatabase();

        // check result
        // - check total number of elements
        Assert.assertEquals(2, DUT.getMap().size());
        // - are there 2 data stores in working-dir
        Assert.assertEquals(3, storageDir.list((dir, name) -> name.startsWith("AccountAgeWitnessStore") && !name.endsWith("_TEST")).length);
        // - do the 3 data stores share objects
        Assert.assertEquals(0, DUT.getMap("since " + getVersion(0)).size());
        Assert.assertEquals(1, DUT.getMap("since " + getVersion(-1)).size());
        Assert.assertEquals(2, DUT.getMap("since " + getVersion(-2)).size());
    }

    /**
     * TEST CASE: test if getMap still return all elements<br><br>
     *
     * USE CASE:
     * The app needs all data for <insert your reason here>. Currently, this is the most
     * encountered use case. Future plans, however, aim to reduce the need for this use
     * case.<br><br>
     *
     * RESULT:
     * getMap returns all elements stored in the various database files.
     */
    @Test
    public void getMap() throws Exception {
        // setup scenario
        // - create 2 data stores containing historical data and a live database
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(-1)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), object2);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object3);

        // simulate Bisq startup
        AppendOnlyDataStoreService DUT = loadDatabase();

        // check result
        // - getMap still gets all the objects?
        Assert.assertEquals(3, DUT.getMap().size());
        Assert.assertTrue(DUT.getMap().containsValue(object1));
        Assert.assertTrue(DUT.getMap().containsValue(object2));
        Assert.assertTrue(DUT.getMap().containsValue(object3));
    }

    /**
     * TEST CASE: test if getMap filtering works<br><br>
     *
     * USE CASE:
     * After introducing the database snapshot functionality, the app only requests objects
     * which it got since the last database snapshot.<br><br>
     *
     * RESULT:
     * getMap(since x) returns all elements added after snapshot x
     */
    @Test
    public void getMapSinceFilter() throws Exception {
        // setup scenario
        // - create 2 data stores containing historical data and a live database
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(-1)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), object2);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object3);

        // simulate bisq startup
        AppendOnlyDataStoreService DUT = loadDatabase();

        // check result
        // - check "live" filter
        Collection<PersistableNetworkPayload> result = DUT.getMap("since " + getVersion(0)).values();
        Assert.assertEquals(1, result.size());
        Assert.assertTrue(result.contains(object3));
        // - check "since version" filter
        result = DUT.getMap("since " + getVersion(-1)).values();
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains(object2));
        Assert.assertTrue(result.contains(object3));
    }

    /**
     * TEST CASE: test if adding new data only adds to live database<br><br>
     *
     * USE CASE:
     * Whenever new objects come in, they are going to be persisted to disk so that
     * the local database is kept in sync with the distributed database.<br><br>
     *
     * RESULT:
     * map.put should only add data to the live database. Other data stores are read-only!
     */
    @Test
    public void put() throws Exception {
        // setup scenario
        // - create one database containing historical data and a live database
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object2);

        // simulate Bisq startup
        AppendOnlyDataStoreService DUT = loadDatabase();
        // add data
        DUT.put(new P2PDataStorage.ByteArray(object3.getHash()), object3);

        // check result
        // - did the live database grow?
        Collection<PersistableNetworkPayload> result = DUT.getMap("since " + getVersion(0)).values();
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains(object2));
        Assert.assertTrue(result.contains(object3));
        // - did the historical data data store grow?
        Assert.assertEquals(1, DUT.getMap().size() - result.size());
    }

    /**
     * TEST CASE: test if only new data is added given a set of data we partially already
     * know.<br><br>
     *
     * USE CASE:
     * Given a Bisq client version x asks a seed node version x-1 for data, it might receive
     * data it already has. We do not want to add duplicates to our local database.<br><br>
     *
     * RESULT:
     * Check for duplicates
     */
    @Test
    public void putDuplicates() throws Exception {
        // setup scenario
        // - create one database containing historical data and a live database
        createDatabase(createFile(false, "AccountAgeWitnessStore_" + getVersion(0)), object1);
        createDatabase(createFile(false, "AccountAgeWitnessStore"), object2);

        // simulate Bisq startup
        AppendOnlyDataStoreService DUT = loadDatabase();
        // add data
        // - duplicate data
        DUT.put(new P2PDataStorage.ByteArray(object1.getHash()), object1);
        // - legit data
        DUT.put(new P2PDataStorage.ByteArray(object3.getHash()), object3);

        // check result
        // - did the live database grow?
        Collection<PersistableNetworkPayload> result = DUT.getMap("since " + getVersion(0)).values();
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains(object2));
        Assert.assertTrue(result.contains(object3));
    }
}
