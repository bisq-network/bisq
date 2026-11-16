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
package bisq.core.payment.stablecoin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Ported from Bisq 2 (bisq.common.asset.StableCoinRepositoryTest), without the tests of the lookups by code
public class StableCoinRepositoryTest {
    @Test
    public void usdtErc20HasCorrectProperties() {
        StableCoin coin = StableCoinRepository.USDT_ERC20;
        assertEquals("USDT", coin.getCode());
        assertEquals("Tether USD", coin.getName());
        assertEquals("USD", coin.getPegCurrencyCode());
        assertEquals(StableCoin.Network.ETHEREUM, coin.getNetwork());
        assertEquals(StableCoin.TokenStandard.ERC20, coin.getTokenStandard());
        assertEquals("0xdAC17F958D2ee523a2206206994597C13D831ec7", coin.getTokenContract());
    }

    @Test
    public void usdcSplHasCorrectNetwork() {
        assertEquals(StableCoin.Network.SOLANA, StableCoinRepository.USDC_SPL.getNetwork());
        assertEquals(StableCoin.TokenStandard.SPL, StableCoinRepository.USDC_SPL.getTokenStandard());
    }
}
