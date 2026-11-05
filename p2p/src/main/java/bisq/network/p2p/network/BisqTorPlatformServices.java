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

import bisq.network.tor.PlatformServices;

import java.nio.file.FileSystems;

/**
 * Passes the platform operations which the Tor library needs. Bisq 2 implements the same interface with its JDK
 * facade, because bisq-mobile runs the library on Android.
 */
class BisqTorPlatformServices implements PlatformServices {
    @Override
    public String getMyPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

    @Override
    public boolean supportsPosixPermissions() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }
}
