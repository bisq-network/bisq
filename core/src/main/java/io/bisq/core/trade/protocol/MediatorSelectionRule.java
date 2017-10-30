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

package io.bisq.core.trade.protocol;

import io.bisq.common.crypto.Hash;
import io.bisq.core.offer.Offer;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class MediatorSelectionRule {
    public static NodeAddress select(List<NodeAddress> acceptedMediatorNodeAddresses, Offer offer) {
        List<NodeAddress> candidates = new ArrayList<>();
        for (NodeAddress offerMediatorNodeAddress : offer.getMediatorNodeAddresses()) {
            candidates.addAll(acceptedMediatorNodeAddresses.stream()
                    .filter(offerMediatorNodeAddress::equals)
                    .collect(Collectors.toList()));
        }
        checkArgument(candidates.size() > 0, "candidates.size() <= 0");

        int index = Math.abs(Arrays.hashCode(Hash.getSha256Hash(offer.getId().getBytes()))) % candidates.size();
        NodeAddress selectedMediator = candidates.get(index);
        log.debug("selectedMediator " + selectedMediator);
        return selectedMediator;
    }
}
