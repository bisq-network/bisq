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

package bisq.core.locale;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class CurrencyTuple {
    public final String code;
    public final String name;
    public final int precision; // precision 4 is 1/10000 -> 0.0001 is smallest unit

    public CurrencyTuple(String code, String name) {
        // We use Fiat class and the precision is 4
        // In future we might add custom precision per currency
        this(code, name, 4);
    }

    public CurrencyTuple(String code, String name, int precision) {
        this.code = code;
        this.name = name;
        this.precision = precision;
    }
}
