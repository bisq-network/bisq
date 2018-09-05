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

package bisq.core.monetary;

import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.MonetaryFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MonetaryWrapper {
    private static final Logger log = LoggerFactory.getLogger(MonetaryWrapper.class);

    /// Instance of Fiat or Altcoin
    protected final Monetary monetary;
    protected final MonetaryFormat fiatFormat = MonetaryFormat.FIAT.repeatOptionalDecimals(0, 0);
    protected final MonetaryFormat altCoinFormat = MonetaryFormat.FIAT.repeatOptionalDecimals(0, 0);

    public MonetaryWrapper(Monetary monetary) {
        this.monetary = monetary;
    }

    public Monetary getMonetary() {
        return monetary;
    }

    public boolean isZero() {
        return monetary.getValue() == 0;
    }

    public int smallestUnitExponent() {
        return monetary.smallestUnitExponent();
    }

    public long getValue() {
        return monetary.getValue();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (o == null || o.getClass() != getClass())
            return false;
        final Monetary otherMonetary = ((MonetaryWrapper) o).getMonetary();
        return monetary.getValue() == otherMonetary.getValue();
    }

    @Override
    public int hashCode() {
        return (int) monetary.getValue();
    }
}
