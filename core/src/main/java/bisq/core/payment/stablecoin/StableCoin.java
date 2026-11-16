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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * A stablecoin on one network. Ported from Bisq 2 (bisq.common.asset.StableCoin).
 * <p>
 * Differences to Bisq 2: no protobuf, as Bisq 1 identifies a coin on a network by the ID of its
 * payment method (see {@link StableCoinPaymentRail}); no issuer, no custom coins and no display
 * names, which no Bisq 1 code uses (the names of a payment method are texts of the application);
 * only the networks and token standards which have a coin. The token contract is added, so that
 * a trader can check that a received token is the listed one.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class StableCoin {
    private final String code;
    private final String name;
    private final String pegCurrencyCode;
    private final Network network;
    private final TokenStandard tokenStandard;
    // The contract which holds the balances of the coin on its network (on Solana the mint).
    // A token with the same code but another contract is another token.
    private final String tokenContract;

    public StableCoin(String code,
                      String name,
                      String pegCurrencyCode,
                      Network network,
                      TokenStandard tokenStandard,
                      String tokenContract) {
        this.code = code;
        this.name = name;
        this.pegCurrencyCode = pegCurrencyCode;
        this.network = network;
        this.tokenStandard = tokenStandard;
        this.tokenContract = tokenContract;
    }

    public enum Network {
        ETHEREUM("Ethereum"),
        TRON("Tron"),
        BNB_SMART_CHAIN("BNB Smart Chain"),
        SOLANA("Solana");

        @Getter
        private final String displayName;

        Network(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum TokenStandard {
        // Ethereum
        ERC20("ERC-20"),

        // Tron
        TRC20("TRC-20"),

        // Solana
        SPL("SPL"),

        // BNB Smart Chain
        BEP20("BEP-20");

        @Getter
        private final String displayName;

        TokenStandard(String displayName) {
            this.displayName = displayName;
        }
    }
}
