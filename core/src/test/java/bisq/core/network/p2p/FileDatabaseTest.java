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
import bisq.core.account.witness.AccountAgeWitnessStorageService;
import bisq.core.account.witness.AccountAgeWitnessStore;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.app.Version;
import bisq.common.storage.Storage;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for migrating to and operating a multi-file file-based data store system
 *
 * TODO these tests are bound to be changed once Bisq migrates to a real database backend
 */
public class FileDatabaseTest {
    // Test fixtures
    static final AccountAgeWitness object1 = new AccountAgeWitness(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}, 1);
    static final AccountAgeWitness object2 = new AccountAgeWitness(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2}, 2);
    static final AccountAgeWitness object3 = new AccountAgeWitness(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3}, 3);

    /**
     * TEST CASE: check if test fixture databases are in place and correct
     *
     * This does not test any business logic, just makes sure the test setup is correct.
     */
    @Test
    public void checkTestFixtures() throws IOException, InterruptedException {
        checkTestFixturesHelper(object1);
        checkTestFixturesHelper(object1, object2);
        checkTestFixturesHelper(object2);
        checkTestFixturesHelper(object2, object3);
        checkTestFixturesHelper(object3);
    }

    private void checkTestFixturesHelper(AccountAgeWitness... objects) throws IOException, InterruptedException {
        createDatabase(createFile(false, "AccountAgeWitnessStore"), objects);
        AppendOnlyDataStoreService DUT = loadDatabase();
        Assert.assertEquals(objects.length, DUT.getMap().size());
        Arrays.stream(objects).forEach(object -> {
            Assert.assertTrue(DUT.getMap().containsValue(object));
        });
    }

    /**
     * TEST CASE: test migration scenario from old database file model to new one
     *
     * USE CASE:
     * We migrate from just having one working-dir database file to having multiple. In
     * detail, the user starts with having one database file in her working directory and
     * one in her resources. After the update, there is still one database in her resources
     * but it is labelled differently.
     *
     * RESULT
     * There are 2 data stores in her working directory, one holding the live database,
     * the other one being the exact and readonly copy of the database in resources. Plus,
     * the 2 data stores do not share any set of objects.
     */
    @Test
    public void migrationScenario() throws IOException, InterruptedException {
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
     * TEST CASE: test Bisq software update scenario
     *
     * USE CASE:
     * Given a newly released Bisq version x, a new differential database is added to
     * the resources. The new database has to be copied to the working directory and the
     * live database has to be stripped of the entries found in the new database.
     *
     * RESULT
     * There are n + 1 data stores in the users working directory, one holding the live
     * database, the other 2 being the exact and readonly copy of the data stores in
     * resources. Plus, the data stores in the working dir do not share any set of objects.
     */
    @Test
    public void updateScenario() throws IOException, InterruptedException {
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
     * TEST CASE: test clean install of Bisq software app
     *
     * USE CASE:
     * A user has a fresh install of Bisq. Ie. there are two database files in resources
     * and none in the working directory.
     *
     * RESULT
     * After startup, there should be 3 data stores in the working directory.
     */
    @Test
    public void freshInstallScenario() throws IOException, InterruptedException {
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
        Assert.assertEquals(2, storageDir.list((dir, name) -> name.startsWith("AccountAgeWitnessStore") && !name.endsWith("_TEST")).length);
        // - do the 3 data stores share objects
        Assert.assertEquals(0, DUT.getMap("since " + getVersion(0)).size());
        Assert.assertEquals(1, DUT.getMap("since " + getVersion(-1)).size());
        Assert.assertEquals(2, DUT.getMap("since " + getVersion(-2)).size());
    }

    /**
     * TEST CASE: test if getMap still return all elements
     *
     * USE CASE:
     * The app needs all data for <insert your reason here>. Currently, this is the most
     * encountered use case. Future plans, however, aim to reduce the need for this use
     * case.
     *
     * RESULT
     * getMap returns all elements stored in the various database files.
     */
    @Test
    public void getMap() throws IOException, InterruptedException {
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
     * TEST CASE: test if getMap filtering works
     *
     * USE CASE:
     * After introducing the database snapshot functionality, the app only requests objects
     * which it got since the last database snapshot.
     *
     * RESULT
     * getMap(since x) returns all elements added after snapshot x
     */
    @Test
    public void getMapSinceFilter() throws IOException, InterruptedException {
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
     * TEST CASE: test if adding new data only adds to live database
     *
     * USE CASE:
     * Whenever new objects come in, they are going to be persisted to disk so that
     * the local database is kept in sync with the distributed database.
     *
     * RESULT
     * map.put should only add data to the live database. Other data stores are read-only!
     */
    @Test
    public void put() throws IOException, InterruptedException {
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
        Collection<PersistableNetworkPayload> result = DUT.getMap("since" + getVersion(0)).values();
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains(object2));
        Assert.assertTrue(result.contains(object3));
        // - did the historical data data store grow?
        Assert.assertEquals(1, DUT.getMap().size() - result.size());
    }

    /////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////// Utils /////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////

    List<File> files = new ArrayList<>();
    static final File storageDir = new File("src/test/resources");

    @After
    public void cleanup() {
        try {
            boolean done = false;
            while (!done) {
                Thread.sleep(100);
                Set<Thread> threads = Thread.getAllStackTraces().keySet();
                done = threads.stream().noneMatch(thread -> thread.getName().startsWith("Save-file-task"));
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (File file : files) {
            file.delete();
        }

        try {
            File backupDir = new File(storageDir + "/backup");
            if (backupDir.exists())
                Files.walk(backupDir.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Arrays.stream(storageDir.list((dir, name) -> name.startsWith("AccountAgeWitnessStore"))).forEach(s -> {
            new File(storageDir + File.separator + s).delete();
        });
    }

    public File createFile(boolean isResourceFile, String name) {
        File tmp;
        if (isResourceFile)
            tmp = new File(ClassLoader.getSystemClassLoader().getResource("").getFile() + File.separator + name);
        else
            tmp = new File(storageDir + File.separator + name);

        files.add(tmp);
        return tmp;
    }

    private AppendOnlyDataStoreService loadDatabase() {
        Storage<AccountAgeWitnessStore> storage = new Storage<>(storageDir, new CorePersistenceProtoResolver(null, null, null, null), null);
        AccountAgeWitnessStorageService storageService = new AccountAgeWitnessStorageService(storageDir, storage);
        final AppendOnlyDataStoreService protectedDataStoreService = new AppendOnlyDataStoreService();
        protectedDataStoreService.addService(storageService);
        protectedDataStoreService.readFromResources("_TEST");
        return protectedDataStoreService;
    }

    private void createDatabase(File target,
                                AccountAgeWitness... objects) throws IOException {

        if (null == objects) {
            return;
        }

        String filename = "";
        filename += Arrays.asList(objects).contains(object1) ? "o1" : "";
        filename += Arrays.asList(objects).contains(object2) ? "o2" : "";
        filename += Arrays.asList(objects).contains(object3) ? "o3" : "";

        File source = new File(storageDir + File.separator + filename);

        if (target.exists())
            target.delete();

        Files.copy(source.toPath(), target.toPath());
    }

    /**
     * note that this function assumes a Bisq version format of x.y.z. It will not work with formats other than that eg. x.yy.z
     * @param offset
     * @return relative version string to the Version.VERSION constant
     */
    public String getVersion(int offset) {
        return new StringBuilder().append(Integer.valueOf(Version.VERSION.replace(".", "")) + offset).insert(2, ".").insert(1, ".").toString();
    }
}
