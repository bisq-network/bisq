package io.bisq.gui.main.overlays.windows.downloadupdate;

import org.junit.Test;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/*
 * This file is part of bisq.
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
public class BisqInstallerTest {
    @Test
    public void call() throws Exception {
    }

    @Test
    public void verifySignature() throws Exception {
        URL url = this.getClass().getResource("/downloadUpdate/test.txt");
        File dataFile = new File(url.getFile());
        url = this.getClass().getResource("/downloadUpdate/test.txt.asc");
        File sigFile = new File(url.getFile());
        url = this.getClass().getResource("/downloadUpdate/F379A1C6.asc");
        File pubKeyFile = new File(url.getFile());

        assertEquals(BisqInstaller.VerifyStatusEnum.OK, BisqInstaller.verifySignature(pubKeyFile, sigFile, dataFile));

        url = this.getClass().getResource("/downloadUpdate/test_bad.txt");
        dataFile = new File(url.getFile());
        url = this.getClass().getResource("/downloadUpdate/test_bad.txt.asc");
        sigFile = new File(url.getFile());
        url = this.getClass().getResource("/downloadUpdate/F379A1C6.asc");
        pubKeyFile = new File(url.getFile());

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

}
