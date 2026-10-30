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

import javax.annotation.Nullable;

/**
 * The options --torrcFile and --torrcOptions, and whether the command line or a config file sets them. Torrc entries
 * which make Tor start a program are accepted only from the command line (see {@link TorrcOverrides}).
 */
public record TorrcSettings(@Nullable File torrcFile,
                            boolean torrcFileSetOnCommandLine,
                            String torrcOptions,
                            boolean torrcOptionsSetOnCommandLine) {
    public static TorrcSettings fromCommandLine(@Nullable File torrcFile, String torrcOptions) {
        return new TorrcSettings(torrcFile, true, torrcOptions, true);
    }
}
