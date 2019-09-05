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

package bisq.network.p2p.network;

import bisq.network.p2p.TestUtils;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests functionality around the export and import hidden service address feature. Please
 * be aware that these tests are not exhaustive.
 */
@SuppressWarnings("ConstantConditions")
@Ignore
public class MultiHSTest {
    int port = 9001;
    static File torWorkingDir = new File(MultiHSTest.class.getSimpleName());
    static File hiddenServiceDir = new File(torWorkingDir, "hiddenservice");
    static String hiddenServiceDirPattern = "\\d{15}";
    static File exportFile = new File(torWorkingDir, "export.bisq");

    /**
     * Device(s) Under Test
     */
    TorNetworkNode DUT, DUT2;

    @Before
    public void setup() {
        DUT = new TorNetworkNode(port, TestUtils.getNetworkProtoResolver(), false,
                new NewTor(torWorkingDir, "", "", new ArrayList<>()));
        DUT2 = new TorNetworkNode(port, TestUtils.getNetworkProtoResolver(), false,
                new NewTor(torWorkingDir, "", "", new ArrayList<>()));
    }

    @After
    public void cleanup() {
        cleanupRecursively(hiddenServiceDir);
        if (exportFile.exists())
            exportFile.delete();
    }

    @AfterClass
    public static void cleanupThoroughly() {
        cleanupRecursively(torWorkingDir);
    }

    static void cleanupRecursively(File current) {
        if (current.isDirectory())
            for (File child : current.listFiles())
                cleanupRecursively(child);

        current.delete();
    }

    static void checkHiddenServiceDirs(boolean checkContent) {
        for (String current : hiddenServiceDir.list())
            Assert.assertTrue(current.matches(hiddenServiceDirPattern));

        if (!checkContent)
            return;

        for (File current : hiddenServiceDir.listFiles())
            // if there are 3 file, a "backup" dir likely is one of them
            Assert.assertEquals(3, current.list().length);
    }

    static void startAndStopDUT(TorNetworkNode DUT) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        DUT.start(new SetupListener() {
            @Override
            public void onTorNodeReady() {
                try {
                    Thread.sleep(2000); // sleep to give other listeners a change to do their stuff. If for example the listener responsible for starting the HS is executed after we already shut down tor, there would be no hs files to export
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                DUT.shutDown(() -> {
                });
                latch.countDown();
            }

            @Override
            public void onHiddenServicePublished() {
                latch.countDown();
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                Assert.fail("setup failed");
            }

            @Override
            public void onRequestCustomBridges() {
                Assert.fail("requested custom bridges");
            }
        });
        latch.await(10, TimeUnit.SECONDS);
    }

    // - start the app and see if one hidden service is created
    @Test
    public void firstLaunch() throws InterruptedException {
        startAndStopDUT(DUT);

        Assert.assertEquals(1, hiddenServiceDir.list().length);
        checkHiddenServiceDirs(true);
    }

    // - renew the hidden service and see if 2 are active
    @Test
    public void renewHiddenService() throws InterruptedException {
        DUT.renewHiddenService();
        DUT.renewHiddenService();
        Assert.assertEquals(2, hiddenServiceDir.list().length);
        checkHiddenServiceDirs(false);
    }

    // - migrate to new structure
    @Test
    public void migrateHiddenService() throws InterruptedException, IOException {
        // get ourselves a valid data structure
        startAndStopDUT(DUT);

        // move files to old structure
        File source = hiddenServiceDir.listFiles()[0];
        for (File current : source.listFiles())
            current.renameTo(new File(hiddenServiceDir, current.getName()));
        source.delete();

        // start up
        startAndStopDUT(DUT2);

        // and see if things got migrated correctly
        Assert.assertEquals(1, hiddenServiceDir.list().length);
        checkHiddenServiceDirs(true);
    }

    // - export hidden service
    @Test
    public void exportHiddenService() throws InterruptedException, IOException {
        startAndStopDUT(DUT);

        DUT.exportHiddenService(exportFile);

        Assert.assertTrue(exportFile.exists());
        Assert.assertTrue(0 < exportFile.length());
    }

    // - import hidden service
    @Test
    public void importHiddenService() throws InterruptedException, IOException {
        startAndStopDUT(DUT);

        DUT.exportHiddenService(exportFile);

        DUT.renewHiddenService();

        startAndStopDUT(DUT2);

        DUT2.clearHiddenServices(new HashSet<>());

        DUT2.importHiddenService(exportFile);

        Assert.assertEquals(2, hiddenServiceDir.list().length);
    }

    // - duplicate hidden service and see if only one is started up and the other one is deleted
    @Test
    public void importDuplicateHiddenService() throws InterruptedException, IOException {
        startAndStopDUT(DUT);

        DUT.exportHiddenService(exportFile);
        DUT.importHiddenService(exportFile);

        startAndStopDUT(DUT2);

        Assert.assertEquals(1, hiddenServiceDir.list().length);
    }

    // - remove hidden services and see if they are removed
    @Test
    public void removeHiddenService() throws InterruptedException {
        DUT.renewHiddenService();
        DUT.renewHiddenService();
        Assert.assertEquals(2, hiddenServiceDir.list().length);

        startAndStopDUT(DUT);

        DUT.clearHiddenServices(new HashSet<>());

        Assert.assertNotEquals("Even the current HS has been deleted!", 0, hiddenServiceDir.list().length);
        Assert.assertEquals(1, hiddenServiceDir.list().length);

        checkHiddenServiceDirs(true);
    }

    // - simulate MacOSs .DS_STORE file
    @Test
    public void macsDsStore() throws InterruptedException, IOException {
        DUT.renewHiddenService();

        File dsStoreFile = new File(hiddenServiceDir, ".DS_STORE");
        dsStoreFile.mkdir();

        startAndStopDUT(DUT);

        Assert.assertEquals(0, dsStoreFile.list().length);
    }
}
