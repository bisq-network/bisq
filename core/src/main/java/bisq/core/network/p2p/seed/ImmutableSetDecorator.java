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

package bisq.core.network.p2p.seed;

import com.google.common.collect.ImmutableSet;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

class ImmutableSetDecorator<T> extends AbstractSet<T> {
    private final Set<T> delegate;

    public ImmutableSetDecorator(Set<T> delegate) {
        this.delegate = ImmutableSet.copyOf(delegate);
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    @Override
    public int size() {
        return delegate.size();
    }
}
