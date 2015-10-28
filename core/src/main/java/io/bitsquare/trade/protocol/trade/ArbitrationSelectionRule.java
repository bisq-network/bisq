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

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.p2p.Address;
import io.bitsquare.trade.offer.Offer;
import org.bitcoinj.core.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class ArbitrationSelectionRule {
    private static final Logger log = LoggerFactory.getLogger(ArbitrationSelectionRule.class);

    public static Address select(List<Address> acceptedArbitratorAddresses, Offer offer) {
        List<Address> candidates = new ArrayList<>();
        for (Address offerArbitratorAddress : offer.getArbitratorAddresses()) {
            candidates.addAll(acceptedArbitratorAddresses.stream().filter(offerArbitratorAddress::equals).collect(Collectors.toList()));
        }
        checkArgument(candidates.size() > 0, "candidates.size() <= 0");

        int index = Math.abs(Sha256Hash.hash(offer.getId().getBytes()).hashCode()) % candidates.size();
        Address selectedArbitrator = candidates.get(index);
        log.debug("selectedArbitrator " + selectedArbitrator);
        return selectedArbitrator;
    }
}
