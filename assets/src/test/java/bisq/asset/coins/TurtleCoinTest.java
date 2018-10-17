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

public class TurtleCoinTest extends AbstractAssetTest {

    public TurtleCoinTest() {
        super(new TurtleCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("TRTLv2X775FNQmN8x2UC3TVzs6trRHwUAcQSL6RUyRXR6JjwFYP8XG8VTCsi7QgPcWBJUWJk2SwaMYvrMk37T4nFVLPigMXcsf8");
        assertValidAddress("TRTLuyTzuoDL9wvoq9VcyGW9Vrp2R3161V3hSa8nZUxAL4iqbTJfFhSXpsrQunXuCGAnA72cZgYGmP7a8zJ6RrwAf5rKjwhUEU8");
        assertValidAddress("TRTLv2YGSbTgmAkZDYvRM8X6bLcJXYr4qMDTXYth9ppc2rHfnNGXPcbBTWxfRxwPTnJvFX1txGh6j9tQ9spJs3US3WwvDzkGsXC");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("TRTLv23ymatPTWgN1jncG33hMdJxZBLrBcCWQBGGGC14CFMUCq1nvxiV8d5cW92mmavzw542bpyjYXd8");
        assertInvalidAddress("TRLuxauCnCH7XZrSZSZw7XEEbkgrnZcaE1MK8wLtTYkF3g1J7nciYiaZDsTNYm2oDLTAM2JPq4rrlhVN5cXWpTPYh8P5wKbXNdoh");
        assertInvalidAddress("");
        assertInvalidAddress("TRTLv3xxpAFfXKwF5ond4sWDX3AVgZngT88KpPCCJKcuRjGktgp5HHTK2yV7NTo8659u5jwMigLmHaoFKho0OhVmF8WP9pVZhBL9kC#RoUKWRwpsx1F");
        assertInvalidAddress("TRTLuwafXHTPzj1d2wc7c9X69r3qG1277ecnLnUaZ61M1YV5d3GYAs1Jbc2q4C4fWN$C4fWNLoDLDvADvpjNYdt3sdRB434UidKXimQQn");
        assertInvalidAddress("1jRo3rcp9fjdfjdSGpx");
        assertInvalidAddress("GDARp92UtmTWDjZatG8sduRockSteadyWasHere3atrHSXr9vJzjHq2TfPrjateDz9Wc8ZJKuDayqJ$%");
        assertInvalidAddress("F3xQ8Gv6xnvDhUrM57z71bfFvu9HeofXtXpZRLnrCN2s2cKvkQowrWjJTGz4676ymKvU4NzPY8Cadgsdhsdfhg4gfJwL2yhhkJ7");
    }
}