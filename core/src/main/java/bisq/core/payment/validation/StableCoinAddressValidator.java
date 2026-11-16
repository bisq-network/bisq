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
import bisq.core.payment.stablecoin.StableCoin;
import bisq.core.util.validation.InputValidator;

import bisq.asset.AddressValidationResult;
import bisq.asset.AddressValidator;
import bisq.asset.EtherAddressValidator;
import bisq.asset.SolanaAddressValidator;
import bisq.asset.TronAddressValidator;

import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;

import java.util.Locale;

import javax.annotation.Nullable;

/**
 * Validates the address of an account on the network of a stablecoin.
 */
public final class StableCoinAddressValidator extends InputValidator {
    private static final String EVM_ADDRESS_PREFIX = "0x";

    private final StableCoin.Network network;
    private final AddressValidator addressValidator;

    public StableCoinAddressValidator(StableCoin stableCoin) {
        network = stableCoin.getNetwork();
        addressValidator = getAddressValidator(network);
    }

    // White space around the address is not part of it. It is removed when the address is stored.
    @Override
    public ValidationResult validate(String input) {
        ValidationResult validationResult = super.validate(input);
        if (!validationResult.isValid) {
            return validationResult;
        }

        String address = input.trim();
        AddressValidationResult result = addressValidator.validate(address);
        if (result.isValid() && isEvmNetwork()) {
            result = validateEvmAddressForm(address);
        }
        if (!result.isValid()) {
            return new ValidationResult(false, Res.get(result.getI18nKey(), network.getDisplayName(),
                    result.getMessage()));
        }
        return new ValidationResult(true);
    }

    /**
     * An Ethereum or BNB Smart Chain address is the same address in any case of its letters. It is stored with the
     * case of its checksum (EIP-55), so the account data does not depend on how the address was entered, and the peer
     * gets an address with a checksum.
     *
     * @return the form in which an address is stored: without white space around it, and a valid Ethereum or BNB
     * Smart Chain address with the case of its checksum.
     */
    @Nullable
    public String toStoredForm(@Nullable String input) {
        if (input == null) {
            return null;
        }
        String address = input.trim();
        if (!isEvmNetwork() || !validate(address).isValid) {
            return address;
        }
        return EVM_ADDRESS_PREFIX + toChecksumCase(address.substring(EVM_ADDRESS_PREFIX.length()));
    }

    private boolean isEvmNetwork() {
        return network == StableCoin.Network.ETHEREUM || network == StableCoin.Network.BNB_SMART_CHAIN;
    }

    // Only the form with the prefix is accepted. The case of the letters carries a checksum (EIP-55), which is checked
    // if the address has letters in both cases. An address in one case has no checksum.
    private static AddressValidationResult validateEvmAddressForm(String address) {
        if (!address.startsWith(EVM_ADDRESS_PREFIX)) {
            return AddressValidationResult.invalidAddress("The address must start with 0x.");
        }

        String hex = address.substring(EVM_ADDRESS_PREFIX.length());
        boolean hasUpperCase = !hex.equals(hex.toLowerCase(Locale.ROOT));
        boolean hasLowerCase = !hex.equals(hex.toUpperCase(Locale.ROOT));
        if (hasUpperCase && hasLowerCase && !hex.equals(toChecksumCase(hex))) {
            return AddressValidationResult.invalidAddress(
                    "The upper and lower case letters do not match the checksum of the address.");
        }
        return AddressValidationResult.validAddress();
    }

    // EIP-55: a hex digit is in upper case if the hex digit at the same position of the Keccak-256 hash of the lower
    // case address is 8 or higher
    private static String toChecksumCase(String hex) {
        String lowerCase = hex.toLowerCase(Locale.ROOT);
        KeccakDigest digest = new KeccakDigest(256);
        byte[] input = lowerCase.getBytes(StandardCharsets.US_ASCII);
        digest.update(input, 0, input.length);
        byte[] hash = new byte[digest.getDigestSize()];
        digest.doFinal(hash, 0);
        String hashAsHex = Hex.toHexString(hash);
        StringBuilder result = new StringBuilder(lowerCase.length());
        for (int i = 0; i < lowerCase.length(); i++) {
            char c = lowerCase.charAt(i);
            result.append(Character.digit(hashAsHex.charAt(i), 16) >= 8 ? Character.toUpperCase(c) : c);
        }
        return result.toString();
    }

    private static AddressValidator getAddressValidator(StableCoin.Network network) {
        switch (network) {
            case ETHEREUM:
            case BNB_SMART_CHAIN:
                // BNB Smart Chain uses the address format of Ethereum
                return new EtherAddressValidator();
            case TRON:
                return new TronAddressValidator();
            case SOLANA:
                return new SolanaAddressValidator();
            default:
                throw new IllegalArgumentException("No address validator for network " + network);
        }
    }
}
