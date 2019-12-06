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

package bisq.network.p2p.peers;

import bisq.network.NetworkOptionKeys;
import bisq.network.p2p.NodeAddress;

import javax.inject.Named;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BanList {
    private static List<NodeAddress> list = new ArrayList<>();

    public static void add(NodeAddress onionAddress) {
        list.add(onionAddress);
    }

    public static boolean isBanned(NodeAddress nodeAddress) {
        return list.contains(nodeAddress);
    }

    @Inject
    public BanList(@Named(NetworkOptionKeys.BAN_LIST) String banList) {
        if (banList != null && !banList.isEmpty())
            BanList.list = Arrays.asList(StringUtils.deleteWhitespace(banList).split(",")).stream().map(NodeAddress::new).collect(Collectors.toList());
    }
}
