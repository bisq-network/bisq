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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * hold a set of capabilities and offers appropriate comparison methods.
 *
 * @author Florian Reimair
 */
@EqualsAndHashCode
public class Capabilities {

    /**
     * The global set of capabilities, i.e. the capabilities if the local app.
     */
    public static final Capabilities app = new Capabilities();

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
        if(capabilities != null)
            this.capabilities.addAll(capabilities.capabilities);
    }

    public boolean containsAll(final Set<Capability> requiredItems) {
        return capabilities.containsAll(requiredItems);
    }

    public boolean containsAll(final Capabilities capabilities) {
        return containsAll(capabilities.capabilities);
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
        return capabilities.capabilities.stream().map(capability -> capability.ordinal()).sorted().collect(Collectors.toList());
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
                .map(integer -> Capability.values()[integer])
                .collect(Collectors.toSet()));
    }

    @Override
    public String toString() {
        return Arrays.toString(Capabilities.toIntList(this).toArray());
    }

    public int size() {
        return capabilities.size();
    }
}
