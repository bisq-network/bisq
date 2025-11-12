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

package bisq.asset;

import java.util.Objects;

import static org.apache.commons.lang3.Validate.notBlank;

/**
 * Abstract base class for {@link Asset} implementations. Most implementations should not
 * extend this class directly, but should rather extend {@link Coin}, {@link Token} or one
 * of their subtypes.
 *
 * @author Chris Beams
 * @since 0.7.0
 */
public abstract class AbstractAsset implements Asset {

    private final String name;
    private final String tickerSymbol;
    private final AddressValidator addressValidator;

    public AbstractAsset(String name, String tickerSymbol, AddressValidator addressValidator) {
        this.name = notBlank(name);
        this.tickerSymbol = notBlank(tickerSymbol);
        this.addressValidator = Objects.requireNonNull(addressValidator);
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String getTickerSymbol() {
        return tickerSymbol;
    }

    @Override
    public final AddressValidationResult validateAddress(String address) {
        return addressValidator.validate(address);
    }

    @Override
    public String toString() {
        return getClass().getName();
    }
}
