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

import bisq.network.p2p.NodeAddress;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Helper for parsing and pretty printing onion addresses.
 *
 * @author Florian Reimair
 */
public class OnionParser {

    public static NodeAddress getNodeAddress(final String current) throws MalformedURLException {
        String nodeAddress = current.trim();
        if (!nodeAddress.startsWith("http://"))
            nodeAddress = "http://" + nodeAddress;
        URL tmp = new URL(nodeAddress);
        return new NodeAddress(tmp.getHost(), tmp.getPort() > 0 ? tmp.getPort() : 80);
    }

    public static String prettyPrint(final NodeAddress host) {
        return host.getHostNameWithoutPostFix();
    }

    public static String prettyPrint(String host) throws MalformedURLException {
        return prettyPrint(getNodeAddress(host));
    }
}
