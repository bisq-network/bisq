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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class PlenteumTest extends AbstractAssetTest {

    public PlenteumTest() {
        super(new Plenteum());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("PLeah9bvqxEDUWbRFqgNcYDeoL772WH9mcCQu9p29MC23NeCUkbVdUEfwDAtF8SgV81kf2hwCdpxqAJmC9k3nJsA7W4UThrufj");
        assertValidAddress("PLeavHTKHz9UcTCSCmd8eihuLxbsK9a7wSpfcYXPYY87JMpvYwwTH6Df32fRLc1r4rQMKoDLpTvywXx4FUVTggCR4jh9PEhvXb");
        assertValidAddress("PLeazd7iQEoFWJttR6353BMvs1cJfMqDmEUk2Z2XSoDdZigY5CbNLvrFUr7duvnEFdSKRdCQYTDkrcySYD1zaFtT9YMubRjHL2");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("PLev23ymatPTWgN1jncG33hMdJxZBLrBcCWQBGGGC14CFMUCq1nvxiV8d5cW92mmavzw542bpyjYXd8");
        assertInvalidAddress("PLeuxauCnCH7XZrSZSZw7XEEbkgrnZcaE1MK8wLtTYkF3g1J7nciYiaZDsTNYm2oDLTAM2JPq4rrlhVN5cXWpTPYh8P5wKbXNdoh");
        assertInvalidAddress("");
        assertInvalidAddress("PLev3xxpAFfXKwF5ond4sWDX3ATpZngT88KpPCCJKcuRjGktgp5HHTK2yV7NTo8687u5jwMigLmHaoFKho0OhVmF8WP9pVZhBL9kC#RoPOWRwpsx1F");
        assertInvalidAddress("PLeuwafXHTPzj1d2wc7c9X69r3qG1277ecnLnUaZ61M1YV5d3GYAs1Jbc2q4C4fWN$C4fWNLoDLDvADvpjNYdt3sdRB434UidKXimQQn");
        assertInvalidAddress("1jRo3rcp9fjdfjdSGpx");
        assertInvalidAddress("GDARp92UtmTWDjZatG8sduRockSteadyWasHere3atrHSXr9vJzjHq2TfPrjateDz9Wc8ZJKuDayqJ$%");
        assertInvalidAddress("F3xQ8Gv6xnvDhUrM57z71bfFvu9HeofXtXpZRLnrCN2s2cKvkQowrWjJTGz4676ymKvU4NzYT5Aadgsdhsdfhg4gfJwL2yhhkJ7");
    }
}