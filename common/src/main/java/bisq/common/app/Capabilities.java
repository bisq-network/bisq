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

package bisq.common.app;

import com.google.common.base.Joiner;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * hold a set of capabilities and offers appropriate comparison methods.
 *
 * @author Florian Reimair
 */
@EqualsAndHashCode
@Slf4j
public class Capabilities {

    /**
     * The global set of capabilities, i.e. the capabilities if the local app.
     */
    public static final Capabilities app = new Capabilities();

    // Defines which most recent capability any node need to support.
    // This helps to clean network from very old inactive but still running nodes.
    @SuppressWarnings("deprecation")
    private static final Capability MANDATORY_CAPABILITY = Capability.DAO_STATE;

    protected final Set<Capability> capabilities = new HashSet<>();

    public Capabilities(Capability... capabilities) {
        this(Arrays.asList(capabilities));
    }

    public Capabilities(Capabilities capabilities) {
        this(capabilities.capabilities);
    }

    public Capabilities(Collection<Capability> capabilities) {
        this.capabilities.addAll(capabilities);
    }

    public void set(Capability... capabilities) {
        set(Arrays.asList(capabilities));
    }

    public void set(Capabilities capabilities) {
        set(capabilities.capabilities);
    }

    public void set(Collection<Capability> capabilities) {
        this.capabilities.clear();
        this.capabilities.addAll(capabilities);
    }

    public void addAll(Capability... capabilities) {
        this.capabilities.addAll(Arrays.asList(capabilities));
    }

    public void addAll(Capabilities capabilities) {
        if (capabilities != null)
            this.capabilities.addAll(capabilities.capabilities);
    }

    public boolean containsAll(final Set<Capability> requiredItems) {
        return capabilities.containsAll(requiredItems);
    }

    public boolean containsAll(final Capabilities capabilities) {
        return containsAll(capabilities.capabilities);
    }

    public boolean containsAll(Capability... capabilities) {
        return this.capabilities.containsAll(Arrays.asList(capabilities));
    }

    public boolean contains(Capability capability) {
        return this.capabilities.contains(capability);
    }

    public boolean isEmpty() {
        return capabilities.isEmpty();
    }


    /**
     * helper for protobuffer stuff
     *
     * @param capabilities
     * @return int list of Capability ordinals
     */
    public static List<Integer> toIntList(Capabilities capabilities) {
        return capabilities.capabilities.stream().map(Enum::ordinal).sorted().collect(Collectors.toList());
    }

    /**
     * helper for protobuffer stuff
     *
     * @param capabilities a list of Capability ordinals
     * @return a {@link Capabilities} object
     */
    public static Capabilities fromIntList(List<Integer> capabilities) {
        return new Capabilities(capabilities.stream()
                .filter(integer -> integer < Capability.values().length)
                .filter(integer -> integer >= 0)
                .map(integer -> Capability.values()[integer])
                .collect(Collectors.toSet()));
    }

    /**
     *
     * @param list      Comma separated list of Capability ordinals.
     * @return Capabilities
     */
    public static Capabilities fromStringList(String list) {
        if (list == null || list.isEmpty())
            return new Capabilities();

        List<String> entries = List.of(list.replace(" ", "").split(","));
        List<Integer> capabilitiesList = entries.stream()
                .map(c -> {
                    try {
                        return Integer.parseInt(c);
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return Capabilities.fromIntList(capabilitiesList);
    }

    /**
     * @return Converts capabilities to list of ordinals as comma separated strings
     */
    public String toStringList() {
        return Joiner.on(", ").join(Capabilities.toIntList(this));
    }

    public static boolean hasMandatoryCapability(Capabilities capabilities) {
        return hasMandatoryCapability(capabilities, MANDATORY_CAPABILITY);
    }

    public static boolean hasMandatoryCapability(Capabilities capabilities, Capability mandatoryCapability) {
        return capabilities.capabilities.stream().anyMatch(c -> c == mandatoryCapability);
    }

    @Override
    public String toString() {
        return Arrays.toString(Capabilities.toIntList(this).toArray());
    }

    public String prettyPrint() {
        return capabilities.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(e -> e.name() + " [" + e.ordinal() + "]")
                .collect(Collectors.joining(", "));
    }

    public int size() {
        return capabilities.size();
    }

    // We return true if our capabilities have less capabilities than the parameter value
    public boolean hasLess(Capabilities other) {
        return findHighestCapability(this) < findHighestCapability(other);
    }

    // We use the sum of all capabilities. Alternatively we could use the highest entry.
    // Neither would support removal of past capabilities, a use case we never had so far and which might have
    // backward compatibility issues, so we should treat capabilities as an append-only data structure.
    public int findHighestCapability(Capabilities capabilities) {
        return (int) capabilities.capabilities.stream()
                .mapToLong(e -> (long) e.ordinal())
                .sum();
    }
}
