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

import java.io.File;
import java.io.IOException;

import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import bisq.common.storage.FileUtil;

/**
 * Holds information on how tor should be created and delivers a respective
 * {@link Tor} object when asked.
 *
 * @author Florian Reimair
 *
 */
public abstract class TorMode {

    protected final File torDir;

    /**
     * @param torDir           points to the place, where we will persist private
     *                         key and address data
     */
    public TorMode(File torDir) {
        this.torDir = torDir;
    }

    /**
     * Returns a fresh {@link Tor} object.
     *
     * @return a fresh instance of {@link Tor}
     * @throws IOException
     * @throws TorCtlException
     */
    public abstract Tor getTor() throws IOException, TorCtlException;

    public File getHiddenServiceBaseDirectory() {
        return new File(torDir, "hiddenservice");
    }
}
