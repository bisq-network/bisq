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

import java.io.Serializable;

import java.util.Objects;

public class Tuple2<A, B> implements Serializable {
    private static final long serialVersionUID = 1;

    final public A first;
    final public B second;

    public Tuple2(A first, B second) {
        this.first = first;
        this.second = second;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tuple2)) return false;

        Tuple2<?, ?> tuple2 = (Tuple2<?, ?>) o;

        if (!Objects.equals(first, tuple2.first)) return false;
        return Objects.equals(second, tuple2.second);

    }

    @Override
    public int hashCode() {
        int result = first != null ? first.hashCode() : 0;
        result = 31 * result + (second != null ? second.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Tuple2{" +
                "\n     first=" + first +
                ",\n     second=" + second +
                "\n}";
    }
}
