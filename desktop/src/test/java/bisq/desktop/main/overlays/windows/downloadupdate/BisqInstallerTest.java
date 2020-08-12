/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.overlays.windows.downloadupdate;

import bisq.desktop.main.overlays.windows.downloadupdate.BisqInstaller.FileDescriptor;

import com.google.common.collect.Lists;

import java.net.URL;

import java.io.File;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@Slf4j
public class BisqInstallerTest {
    @Test
    public void call() throws Exception {
    }

    @Test
    public void verifySignature() throws Exception {
        URL url = this.getClass().getResource("/downloadUpdate/test.txt");
        File dataFile = new File(url.toURI().getPath());
        url = this.getClass().getResource("/downloadUpdate/test.txt.asc");
        File sigFile = new File(url.toURI().getPath());
        url = this.getClass().getResource("/downloadUpdate/F379A1C6.asc");
        File pubKeyFile = new File(url.toURI().getPath());

        assertEquals(BisqInstaller.VerifyStatusEnum.OK, BisqInstaller.verifySignature(pubKeyFile, sigFile, dataFile));

        url = this.getClass().getResource("/downloadUpdate/test_bad.txt");
        dataFile = new File(url.toURI().getPath());
        url = this.getClass().getResource("/downloadUpdate/test_bad.txt.asc");
        sigFile = new File(url.toURI().getPath());
        url = this.getClass().getResource("/downloadUpdate/F379A1C6.asc");
        pubKeyFile = new File(url.toURI().getPath());

        BisqInstaller.verifySignature(pubKeyFile, sigFile, dataFile);
        assertEquals(BisqInstaller.VerifyStatusEnum.FAIL, BisqInstaller.verifySignature(pubKeyFile, sigFile, dataFile));
    }

    @Test
    public void getFileName() throws Exception {
    }

    @Test
    public void getDownloadType() throws Exception {
    }

    @Test
    public void getIndex() throws Exception {
    }

    @Test
    public void getSigFileDescriptors() throws Exception {
        BisqInstaller bisqInstaller = new BisqInstaller();
        FileDescriptor installerFileDescriptor = FileDescriptor.builder().fileName("filename.txt").id("filename").loadUrl("url://filename.txt").build();
        FileDescriptor key1 = FileDescriptor.builder().fileName("key1").id("key1").loadUrl("").build();
        FileDescriptor key2 = FileDescriptor.builder().fileName("key2").id("key2").loadUrl("").build();
        List<FileDescriptor> sigFileDescriptors = bisqInstaller.getSigFileDescriptors(installerFileDescriptor, Lists.newArrayList(key1));
        assertEquals(1, sigFileDescriptors.size());
        sigFileDescriptors = bisqInstaller.getSigFileDescriptors(installerFileDescriptor, Lists.newArrayList(key1, key2));
        assertEquals(2, sigFileDescriptors.size());
        log.info("test");

    }
}
