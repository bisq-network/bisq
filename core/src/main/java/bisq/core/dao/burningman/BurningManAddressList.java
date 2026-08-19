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

package bisq.core.dao.burningman;

import bisq.common.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class BurningManAddressList {
    public static final int SCHEMA_VERSION = 1;

    private int schemaVersion;
    private int listVersion;
    private String network;
    private int chainHeight;
    private int burningManSelectionHeight;
    private String legacyBurningManAddress;
    private List<Entry> entries;

    @SuppressWarnings("unused")
    private BurningManAddressList() {
    }

    public BurningManAddressList(int schemaVersion,
                                 int listVersion,
                                 String network,
                                 int chainHeight,
                                 int burningManSelectionHeight,
                                 String legacyBurningManAddress,
                                 List<Entry> entries) {
        this.schemaVersion = schemaVersion;
        this.listVersion = listVersion;
        this.network = network;
        this.chainHeight = chainHeight;
        this.burningManSelectionHeight = burningManSelectionHeight;
        this.legacyBurningManAddress = legacyBurningManAddress;
        this.entries = new ArrayList<>(entries);
    }

    public boolean isForCurrentNetwork() {
        return Config.baseCurrencyNetwork().name().equals(network);
    }

    public Set<String> getReceiverAddresses() {
        return entries.stream()
                .map(Entry::getReceiverAddress)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public Set<String> getAllowedAddresses() {
        Set<String> allowedAddresses = getReceiverAddresses();
        allowedAddresses.add(legacyBurningManAddress);
        return allowedAddresses;
    }

    public Map<String, Double> getCappedBurnAmountShareByAddress() {
        return entries.stream()
                .collect(Collectors.toMap(Entry::getReceiverAddress,
                        Entry::getCappedBurnAmountShare,
                        (first, second) -> first,
                        TreeMap::new));
    }

    @Getter
    @EqualsAndHashCode
    public static final class Entry {
        private String receiverAddress;
        private double cappedBurnAmountShare;

        @SuppressWarnings("unused")
        private Entry() {
        }

        public Entry(String receiverAddress, double cappedBurnAmountShare) {
            this.receiverAddress = receiverAddress;
            this.cappedBurnAmountShare = cappedBurnAmountShare;
        }
    }
}
