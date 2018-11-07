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

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Abstract base class for all {@link Asset} unit tests. Subclasses must implement the
 * {@link #testValidAddresses()} and {@link #testInvalidAddresses()} methods, and are
 * expected to use the convenient {@link #assertValidAddress(String)} and
 * {@link #assertInvalidAddress(String)} assertions when doing so.
 * <p>
 * Blank / empty addresses are tested automatically by this base class and are always
 * considered invalid.
 * <p>
 * This base class also serves as a kind of integration test for {@link AssetRegistry}, in
 * that all assets tested through subclasses are tested to make sure they are also
 * properly registered and available there.
 *
 * @author Chris Beams
 * @author Bernard Labno
 * @since 0.7.0
 */
public abstract class AbstractAssetTest {

    private final AssetRegistry assetRegistry = new AssetRegistry();

    protected final Asset asset;

    public AbstractAssetTest(Asset asset) {
        this.asset = asset;
    }

    @Test
    public void testPresenceInAssetRegistry() {
        assertThat(asset + " is not registered in META-INF/services/" + Asset.class.getName(),
                assetRegistry.stream().anyMatch(this::hasSameTickerSymbol), is(true));
    }

    @Test
    public void testBlank() {
        assertInvalidAddress("");
    }

    @Test
    public abstract void testValidAddresses();

    @Test
    public abstract void testInvalidAddresses();

    protected void assertValidAddress(String address) {
        AddressValidationResult result = asset.validateAddress(address);
        assertThat(result.getMessage(), result.isValid(), is(true));
    }

    protected void assertInvalidAddress(String address) {
        assertThat(asset.validateAddress(address).isValid(), is(false));
    }

    private boolean hasSameTickerSymbol(Asset asset) {
        return this.asset.getTickerSymbol().equals(asset.getTickerSymbol());
    }
}
