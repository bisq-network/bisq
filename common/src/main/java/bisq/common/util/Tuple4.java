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

public class Tuple4<A, B, C, D> {
    final public A first;
    final public B second;
    final public C third;
    final public D forth;

    public Tuple4(A first, B second, C third, D forth) {
        this.first = first;
        this.second = second;
        this.third = third;
        this.forth = forth;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tuple4)) return false;

        Tuple4<?, ?, ?, ?> tuple4 = (Tuple4<?, ?, ?, ?>) o;

        if (first != null ? !first.equals(tuple4.first) : tuple4.first != null) return false;
        if (second != null ? !second.equals(tuple4.second) : tuple4.second != null) return false;
        if (third != null ? !third.equals(tuple4.third) : tuple4.third != null) return false;
        return !(forth != null ? !forth.equals(tuple4.forth) : tuple4.forth != null);

    }

    @Override
    public int hashCode() {
        int result = first != null ? first.hashCode() : 0;
        result = 31 * result + (second != null ? second.hashCode() : 0);
        result = 31 * result + (third != null ? third.hashCode() : 0);
        result = 31 * result + (forth != null ? forth.hashCode() : 0);
        return result;
    }
}
