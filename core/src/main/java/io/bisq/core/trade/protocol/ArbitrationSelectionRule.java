/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol;

import io.bisq.core.offer.Offer;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import org.bitcoinj.core.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class ArbitrationSelectionRule {
    private static final Logger log = LoggerFactory.getLogger(ArbitrationSelectionRule.class);

    public static NodeAddress select(List<NodeAddress> acceptedArbitratorNodeAddresses, Offer offer) {
        List<NodeAddress> candidates = new ArrayList<>();
        for (NodeAddress offerArbitratorNodeAddress : offer.getArbitratorNodeAddresses()) {
            candidates.addAll(acceptedArbitratorNodeAddresses.stream().filter(offerArbitratorNodeAddress::equals).collect(Collectors.toList()));
        }
        checkArgument(candidates.size() > 0, "candidates.size() <= 0");

        int index = Math.abs(Arrays.hashCode(Sha256Hash.hash(offer.getId().getBytes()))) % candidates.size();
        NodeAddress selectedArbitrator = candidates.get(index);
        log.debug("selectedArbitrator " + selectedArbitrator);
        return selectedArbitrator;
    }
}
