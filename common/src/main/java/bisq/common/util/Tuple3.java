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

package bisq.common.util;

import java.util.Objects;

public class Tuple3<A, B, C> {
    final public A first;
    final public B second;
    final public C third;

    public Tuple3(A first, B second, C third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tuple3)) return false;

        Tuple3<?, ?, ?> tuple3 = (Tuple3<?, ?, ?>) o;

        if (!Objects.equals(first, tuple3.first)) return false;
        if (!Objects.equals(second, tuple3.second)) return false;
        return Objects.equals(third, tuple3.third);

    }

    @Override
    public int hashCode() {
        int result = first != null ? first.hashCode() : 0;
        result = 31 * result + (second != null ? second.hashCode() : 0);
        result = 31 * result + (third != null ? third.hashCode() : 0);
        return result;
    }
}
