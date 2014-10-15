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

package io.bitsquare.msg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// TODO Might be better with a config file
public class SeedNodeAddress {
    private final String id;
    private final String ip;
    private final int port;

    public SeedNodeAddress(StaticSeedNodeAddresses staticSeedNodeAddresses) {
        this(staticSeedNodeAddresses.getId(), staticSeedNodeAddresses.getIp(), staticSeedNodeAddresses.getPort());
    }

    public SeedNodeAddress(String id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
    }

    public String getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum StaticSeedNodeAddresses {
        // Manfreds server: "188.226.179.109"
        // Steves server: "128.199.251.106"
        DIGITAL_OCEAN1("digitalocean1.bitsquare.io", "188.226.179.109", 5000),
        DIGITAL_OCEAN2("digitalocean2.bitsquare.io", "128.199.251.106", 5000),
        LOCALHOST("localhost", "127.0.0.1", 5000);

        private final String id;
        private final String ip;
        private final int port;

        StaticSeedNodeAddresses(String id, String ip, int port) {
            this.id = id;
            this.ip = ip;
            this.port = port;
        }

        public static List<StaticSeedNodeAddresses> getAllSeedNodeAddresses() {
            return new ArrayList<>(Arrays.asList(StaticSeedNodeAddresses.values()));
        }

        public String getId() {
            return id;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }
    }
}
