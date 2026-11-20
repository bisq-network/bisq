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
package bisq.core.payment.validation;

import bisq.core.locale.Res;
import bisq.core.payment.stablecoin.StableCoinRepository;
import bisq.core.util.validation.InputValidator;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;

import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StableCoinAddressValidatorTest {
    private static final String ETHEREUM_ADDRESS = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
    private static final String TRON_ADDRESS = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
    private static final String SOLANA_ADDRESS = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB";

    @BeforeAll
    public static void setUp() {
        BaseCurrencyNetwork baseCurrencyNetwork = Config.baseCurrencyNetwork();
        Res.setBaseCurrencyCode(baseCurrencyNetwork.getCurrencyCode());
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
    }

    @Test
    public void ethereumAndBnbSmartChainAcceptOnlyTheirAddressFormat() {
        for (InputValidator validator : new InputValidator[]{
                new StableCoinAddressValidator(StableCoinRepository.USDT_ERC20),
                new StableCoinAddressValidator(StableCoinRepository.USDT_BEP20)}) {
            assertTrue(validator.validate(ETHEREUM_ADDRESS).isValid);
            assertFalse(validator.validate(TRON_ADDRESS).isValid);
            assertFalse(validator.validate(SOLANA_ADDRESS).isValid);
            assertFalse(validator.validate(ETHEREUM_ADDRESS + "0").isValid);
            assertFalse(validator.validate("").isValid);
            assertFalse(validator.validate(null).isValid);
        }
    }

    @Test
    public void ethereumAddressNeedsThePrefix() {
        InputValidator validator = new StableCoinAddressValidator(StableCoinRepository.USDT_ERC20);
        assertFalse(validator.validate("dAC17F958D2ee523a2206206994597C13D831ec7").isValid);
        assertFalse(validator.validate("0XdAC17F958D2ee523a2206206994597C13D831ec7").isValid);
    }

    // The case of the letters is the checksum of an Ethereum address (EIP-55). It finds a mistyped digit.
    @Test
    public void ethereumAddressWithLettersInBothCasesNeedsAValidChecksum() {
        InputValidator validator = new StableCoinAddressValidator(StableCoinRepository.USDT_ERC20);
        assertTrue(validator.validate(ETHEREUM_ADDRESS).isValid);
        // One digit changed
        assertFalse(validator.validate("0xdAC17F958D2ee523a2206206994597C13D831ec8").isValid);
        // One letter in the other case
        assertFalse(validator.validate("0xDAC17F958D2ee523a2206206994597C13D831ec7").isValid);
        // No checksum: all letters in lower or in upper case
        assertTrue(validator.validate("0xdac17f958d2ee523a2206206994597c13d831ec7").isValid);
        assertTrue(validator.validate("0xDAC17F958D2EE523A2206206994597C13D831EC7").isValid);
        // No letters at all
        assertTrue(validator.validate("0x1111111111111111111111111111111111111111").isValid);

        InputValidator.ValidationResult result = validator.validate("0xdAC17F958D2ee523a2206206994597C13D831ec8");
        assertTrue(result.errorMessage.contains("checksum"), result.errorMessage);
    }

    // The same address in another case of its letters must not be other account data
    @Test
    public void ethereumAddressIsStoredWithTheCaseOfItsChecksum() {
        for (StableCoinAddressValidator validator : new StableCoinAddressValidator[]{
                new StableCoinAddressValidator(StableCoinRepository.USDT_ERC20),
                new StableCoinAddressValidator(StableCoinRepository.USDT_BEP20)}) {
            String hex = ETHEREUM_ADDRESS.substring(2);
            assertEquals(ETHEREUM_ADDRESS, validator.toStoredForm(ETHEREUM_ADDRESS));
            assertEquals(ETHEREUM_ADDRESS, validator.toStoredForm("0x" + hex.toLowerCase(Locale.ROOT)));
            assertEquals(ETHEREUM_ADDRESS, validator.toStoredForm("0x" + hex.toUpperCase(Locale.ROOT)));
            assertTrue(validator.validate(validator.toStoredForm("0x" + hex.toLowerCase(Locale.ROOT))).isValid);
        }
    }

    // A pasted address often has white space around it
    @Test
    public void whiteSpaceAroundAnAddressIsNotPartOfIt() {
        StableCoinAddressValidator ethereum = new StableCoinAddressValidator(StableCoinRepository.USDT_ERC20);
        String hex = ETHEREUM_ADDRESS.substring(2);
        assertTrue(ethereum.validate(" " + ETHEREUM_ADDRESS + "\n").isValid);
        assertEquals(ETHEREUM_ADDRESS, ethereum.toStoredForm(" " + ETHEREUM_ADDRESS + "\n"));
        assertEquals(ETHEREUM_ADDRESS, ethereum.toStoredForm("\t0x" + hex.toLowerCase(Locale.ROOT) + " "));
        assertFalse(ethereum.validate("0x" + hex.substring(0, 20) + " " + hex.substring(20)).isValid);

        StableCoinAddressValidator tron = new StableCoinAddressValidator(StableCoinRepository.USDT_TRC20);
        assertTrue(tron.validate(" " + TRON_ADDRESS + " ").isValid);
        assertEquals(TRON_ADDRESS, tron.toStoredForm(" " + TRON_ADDRESS + " "));
        assertFalse(tron.validate(" ").isValid);
    }

    @Test
    public void invalidAddressIsNotChangedForStoring() {
        StableCoinAddressValidator validator = new StableCoinAddressValidator(StableCoinRepository.USDT_ERC20);
        String wrongChecksum = "0xDAC17F958D2ee523a2206206994597C13D831ec7";
        assertFalse(validator.validate(wrongChecksum).isValid);
        assertEquals(wrongChecksum, validator.toStoredForm(wrongChecksum));
        assertEquals("0xdac17f", validator.toStoredForm("0xdac17f"));
        assertEquals("", validator.toStoredForm(""));
        assertNull(validator.toStoredForm(null));
    }

    // The case of the letters is part of a Tron and of a Solana address
    @Test
    public void tronAndSolanaAddressAreStoredAsEntered() {
        assertEquals(TRON_ADDRESS,
                new StableCoinAddressValidator(StableCoinRepository.USDT_TRC20).toStoredForm(TRON_ADDRESS));
        assertEquals(SOLANA_ADDRESS,
                new StableCoinAddressValidator(StableCoinRepository.USDT_SPL).toStoredForm(SOLANA_ADDRESS));
    }

    @Test
    public void tronAcceptsOnlyItsAddressFormat() {
        InputValidator validator = new StableCoinAddressValidator(StableCoinRepository.USDT_TRC20);
        assertTrue(validator.validate(TRON_ADDRESS).isValid);
        assertFalse(validator.validate(ETHEREUM_ADDRESS).isValid);
        assertFalse(validator.validate(SOLANA_ADDRESS).isValid);
        // Wrong checksum
        assertFalse(validator.validate("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6u").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void solanaAcceptsOnlyItsAddressFormat() {
        InputValidator validator = new StableCoinAddressValidator(StableCoinRepository.USDT_SPL);
        assertTrue(validator.validate(SOLANA_ADDRESS).isValid);
        assertFalse(validator.validate(ETHEREUM_ADDRESS).isValid);
        assertFalse(validator.validate(TRON_ADDRESS).isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void errorMessageNamesTheNetwork() {
        InputValidator.ValidationResult result =
                new StableCoinAddressValidator(StableCoinRepository.USDT_TRC20).validate(ETHEREUM_ADDRESS);
        assertFalse(result.isValid);
        assertTrue(result.errorMessage.contains("Tron"), result.errorMessage);
    }
}
