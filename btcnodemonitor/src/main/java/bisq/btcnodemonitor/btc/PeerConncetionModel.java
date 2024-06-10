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

package bisq.btcnodemonitor.btc;

import bisq.core.btc.nodes.BtcNodes;

import org.bitcoinj.core.PeerAddress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public class PeerConncetionModel {
    private final Map<String, PeerConncetionInfo> map = new HashMap<>();
    private final List<BtcNodes.BtcNode> providedBtcNodes;
    private final Runnable onChangeHandler;

    public PeerConncetionModel(List<BtcNodes.BtcNode> providedBtcNodes, Runnable onChangeHandler) {
        this.providedBtcNodes = providedBtcNodes;
        this.onChangeHandler = onChangeHandler;
    }

    public void fill(Set<PeerAddress> peerAddresses) {
        map.clear();
        map.putAll(peerAddresses.stream()
                .map(peerAddress -> new PeerConncetionInfo(peerAddress, onChangeHandler))
                .collect(Collectors.toMap(PeerConncetionInfo::getAddress, e -> e)));
    }
}
