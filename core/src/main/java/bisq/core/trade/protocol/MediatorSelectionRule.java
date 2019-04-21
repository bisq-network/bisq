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

package bisq.core.trade.protocol;

import bisq.core.offer.Offer;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Hash;

import com.google.common.base.Charsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

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

        int index = Math.abs(Arrays.hashCode(Hash.getSha256Hash(offer.getId().getBytes(Charsets.UTF_8)))) % candidates.size();
        NodeAddress selectedMediator = candidates.get(index);
        log.debug("selectedMediator " + selectedMediator);
        return selectedMediator;
    }
}
