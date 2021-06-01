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

package bisq.desktop.components;

import bisq.desktop.util.DisplayUtils;

import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import javafx.scene.paint.Color;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PeerInfoIconDispute extends PeerInfoIcon {

    public PeerInfoIconDispute(NodeAddress nodeAddress,
                         String nrOfDisputes,
                         long accountAge,
                         Preferences preferences) {
        super(nodeAddress, preferences);

        String accountAgeTooltip = accountAge > -1 ?
                Res.get("peerInfoIcon.tooltip.age", DisplayUtils.formatAccountAge(accountAge)) :
                Res.get("peerInfoIcon.tooltip.unknownAge");

        tooltipText = Res.get("peerInfoIcon.tooltip.dispute", fullAddress, nrOfDisputes, accountAgeTooltip);

        // outer circle always display gray
        Color ringColor = Color.rgb(128, 128, 128);
        createAvatar(ringColor);
        addMouseListener(numTrades, null, null, null, preferences, false,
                false, accountAge, 0L, null, null, null);
    }

    public void refreshTag() {
        updatePeerInfoIcon();
    }
}
