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

package bisq.monitor;

import bisq.network.p2p.network.TorMode;

import org.berndpruenster.netlayer.tor.Tor;

import java.io.File;

/**
 * This class uses an already defined Tor via <code>Tor.getDefault()</code>
 *
 * @author Florian Reimair
 *
 */
public class AvailableTor extends TorMode {

    private final String hiddenServiceDirectory;

    public AvailableTor(File torWorkingDirectory, String hiddenServiceDirectory) {
        super(torWorkingDirectory);

        this.hiddenServiceDirectory = hiddenServiceDirectory;
    }

    @Override
    public Tor getTor() {
        return Tor.getDefault();
    }

    @Override
    public String getHiddenServiceDirectory() {
        return hiddenServiceDirectory;
    }

}
