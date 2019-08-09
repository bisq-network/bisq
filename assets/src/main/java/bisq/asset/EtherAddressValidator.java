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

/**
 * Validates an Ethereum address using the regular expression on record in the
 * <a href="https://github.com/ethereum/web3.js/blob/bd6a890/lib/utils/utils.js#L405">
 * ethereum/web3.js</a> project. Note that this implementation is widely used, not just
 * for actual {@link bisq.asset.coins.Ether} address validation, but also for
 * {@link Erc20Token} implementations and other Ethereum-based {@link Asset}
 * implementations.
 *
 * @author Chris Beams
 * @since 0.7.0
 */
public class EtherAddressValidator extends RegexAddressValidator {

    public EtherAddressValidator() {
        super("^(0x)?[0-9a-fA-F]{40}$");
    }

    public EtherAddressValidator(String errorMessageI18nKey) {
        super("^(0x)?[0-9a-fA-F]{40}$", errorMessageI18nKey);
    }
}
