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

import bisq.core.payment.validation.StableCoinAddressValidator;

import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Ported from Bisq 2 (bisq.account.payment_method.stable_coin.StableCoinPaymentRailTest), with tests of the
// rail names and of the token contracts
public class StableCoinPaymentRailTest {
    @Test
    public void eachRailReferencesItsOwnStableCoin() {
        Set<StableCoin> coins = new HashSet<>();
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            assertNotNull(rail.getStableCoin(), "Rail " + rail.name() + " should have non-null StableCoin");
            assertTrue(coins.add(rail.getStableCoin()), "Rail " + rail.name() + " shares its coin with another rail");
        }
    }

    @Test
    public void usdtRailsMapToCorrectCoin() {
        assertRail(StableCoinPaymentRail.USDT_ERC20, "USDT", StableCoin.Network.ETHEREUM, StableCoin.TokenStandard.ERC20);
        assertRail(StableCoinPaymentRail.USDT_TRC20, "USDT", StableCoin.Network.TRON, StableCoin.TokenStandard.TRC20);
        assertRail(StableCoinPaymentRail.USDT_BEP20, "USDT", StableCoin.Network.BNB_SMART_CHAIN, StableCoin.TokenStandard.BEP20);
        assertRail(StableCoinPaymentRail.USDT_SPL, "USDT", StableCoin.Network.SOLANA, StableCoin.TokenStandard.SPL);
    }

    @Test
    public void usdcAndDaiRailsMapToCorrectCoin() {
        assertRail(StableCoinPaymentRail.USDC_ERC20, "USDC", StableCoin.Network.ETHEREUM, StableCoin.TokenStandard.ERC20);
        assertRail(StableCoinPaymentRail.USDC_SPL, "USDC", StableCoin.Network.SOLANA, StableCoin.TokenStandard.SPL);
        assertRail(StableCoinPaymentRail.DAI_ERC20, "DAI", StableCoin.Network.ETHEREUM, StableCoin.TokenStandard.ERC20);
    }

    // The name of a rail is the ID of its payment method: the code of the coin in upper case and the token standard
    @Test
    public void nameOfEachRailIsCodeAndTokenStandard() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            StableCoin coin = rail.getStableCoin();
            assertEquals(coin.getCode().toUpperCase() + "_" + coin.getTokenStandard().name(), rail.name());
        }
    }

    @Test
    public void tokenStandardOfEachRailBelongsToItsNetwork() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            StableCoin coin = rail.getStableCoin();
            switch (coin.getNetwork()) {
                case ETHEREUM:
                    assertEquals(StableCoin.TokenStandard.ERC20, coin.getTokenStandard(), rail.name());
                    break;
                case TRON:
                    assertEquals(StableCoin.TokenStandard.TRC20, coin.getTokenStandard(), rail.name());
                    break;
                case BNB_SMART_CHAIN:
                    assertEquals(StableCoin.TokenStandard.BEP20, coin.getTokenStandard(), rail.name());
                    break;
                case SOLANA:
                    assertEquals(StableCoin.TokenStandard.SPL, coin.getTokenStandard(), rail.name());
                    break;
                default:
                    throw new AssertionError("Unexpected network of " + rail.name());
            }
        }
    }

    @Test
    public void findReturnsRailByName() {
        assertEquals(Optional.of(StableCoinPaymentRail.USDT_TRC20), StableCoinPaymentRail.find("USDT_TRC20"));
        assertTrue(StableCoinPaymentRail.find("SEPA").isEmpty());
        assertTrue(StableCoinPaymentRail.find("usdt_trc20").isEmpty());
        assertTrue(StableCoinPaymentRail.find("").isEmpty());
        assertTrue(StableCoinPaymentRail.find(null).isEmpty());
    }

    // A token contract is an address on the network of the coin, so a typing error in one is found here: by the
    // checksum of a Tron address, by the mixed-case checksum (EIP-55) of an Ethereum or BNB Smart Chain address and
    // by the length of a Solana address.
    @Test
    public void tokenContractOfEachRailIsAnAddressOnItsNetwork() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            StableCoin coin = rail.getStableCoin();
            String tokenContract = coin.getTokenContract();
            assertTrue(new StableCoinAddressValidator(coin).validate(tokenContract).isValid, rail.name());
            if (coin.getNetwork() == StableCoin.Network.ETHEREUM ||
                    coin.getNetwork() == StableCoin.Network.BNB_SMART_CHAIN) {
                assertEquals(toChecksumAddress(tokenContract), tokenContract, rail.name());
            }
        }
    }

    private static void assertRail(StableCoinPaymentRail rail,
                                   String code,
                                   StableCoin.Network network,
                                   StableCoin.TokenStandard tokenStandard) {
        StableCoin coin = rail.getStableCoin();
        assertEquals(code, coin.getCode());
        assertEquals(network, coin.getNetwork());
        assertEquals(tokenStandard, coin.getTokenStandard());
    }

    // EIP-55: a hex digit of the address is in upper case if the matching hex digit of the Keccak-256 hash of the
    // lower case address is 8 or higher
    private static String toChecksumAddress(String address) {
        String lowerCase = address.substring(2).toLowerCase();
        KeccakDigest digest = new KeccakDigest(256);
        byte[] input = lowerCase.getBytes(StandardCharsets.US_ASCII);
        digest.update(input, 0, input.length);
        byte[] hash = new byte[32];
        digest.doFinal(hash, 0);
        String hashAsHex = Hex.toHexString(hash);
        StringBuilder result = new StringBuilder("0x");
        for (int i = 0; i < lowerCase.length(); i++) {
            char c = lowerCase.charAt(i);
            boolean upperCase = Character.digit(hashAsHex.charAt(i), 16) >= 8;
            result.append(upperCase ? Character.toUpperCase(c) : c);
        }
        return result.toString();
    }
}
