/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Version {
    private static final Logger log = LoggerFactory.getLogger(Version.class);

    // The application versions
    private static final int MAJOR_VERSION = 0;
    private static final int MINOR_VERSION = 3;
    // used as updateFX index
    public static final int PATCH_VERSION = 2;

    public static final String VERSION = MAJOR_VERSION + "." + MINOR_VERSION + "." + PATCH_VERSION;

    // The version nr. for the objects sent over the network. A change will break the serialization of old objects.
    // If objects are used for both network and database the network version is applied.
    public static final long NETWORK_PROTOCOL_VERSION = 1;

    // The version nr. of the serialized data stored to disc. A change will break the serialization of old objects.
    public static final long LOCAL_DB_VERSION = 1;

    // The version nr. of the current protocol. The offer holds that version. A taker will check the version of the offers to see if he his version is 
    // compatible.
    public static final long PROTOCOL_VERSION = 1;

    // The version for the bitcoin network (Mainnet = 0, TestNet = 1, Regtest = 2)
    public static int NETWORK_ID;

    public static void printVersion() {
        log.info("Version{" +
                "VERSION=" + VERSION +
                ", NETWORK_PROTOCOL_VERSION=" + NETWORK_PROTOCOL_VERSION +
                ", LOCAL_DB_VERSION=" + LOCAL_DB_VERSION +
                ", PROTOCOL_VERSION=" + PROTOCOL_VERSION +
                ", NETWORK_ID=" + NETWORK_ID +
                '}');
    }

}
