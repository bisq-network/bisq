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

package bisq.core.dao.state.model.governance;

import bisq.core.app.BisqEnvironment;
import bisq.core.locale.Res;

import lombok.Getter;


/**
 * Data here must not be changed as it would break backward compatibility! In case we need to change we need to add a
 * new entry and maintain the old one. Once all the role holders of an old deprecated role have revoked the
 * role might get removed.
 *
 * Add entry to translation file "dao.bond.bondedRoleType...."
 */
public enum BondedRoleType {
    // admins
    GITHUB_ADMIN(50_000, 60, "https://bisq.network/roles/16", true),
    FORUM_ADMIN(20_000, 60, "https://bisq.network/roles/19", true),
    TWITTER_ADMIN(20_000, 60, "https://bisq.network/roles/21", true),
    ROCKET_CHAT_ADMIN(20_000, 60, "https://bisq.network/roles/79", true),
    YOUTUBE_ADMIN(5_000, 60, "https://bisq.network/roles/56", true),

    // maintainers
    BISQ_MAINTAINER(50_000, 60, "https://bisq.network/roles/63", true),

    // operators
    WEBSITE_OPERATOR(50_000, 60, "https://bisq.network/roles/12", true),
    FORUM_OPERATOR(50_000, 60, "https://bisq.network/roles/19", true),
    SEED_NODE_OPERATOR(20_000, 60, "https://bisq.network/roles/15", true),
    PRICE_NODE_OPERATOR(20_000, 60, "https://bisq.network/roles/14", true),
    BTC_NODE_OPERATOR(5_000, 60, "https://bisq.network/roles/67", true),
    MARKETS_OPERATOR(20_000, 60, "https://bisq.network/roles/9", true),
    BSQ_EXPLORER_OPERATOR(20_000, 60, "https://bisq.network/roles/11", true),

    // other
    DOMAIN_NAME_HOLDER(50_000, 60, "https://bisq.network/roles/77", false),
    DNS_ADMIN(50_000, 60, "https://bisq.network/roles/18", false),
    MEDIATOR(10_000, 60, "N/A", true),
    ARBITRATOR(200_000, 60, "https://bisq.network/roles/13", true);


    // Satoshi value of BSQ bond
    @Getter
    private final long requiredBond;

    // Unlock time in blocks
    @Getter
    private final int unlockTimeInBlocks;
    @Getter
    private final String link;
    @Getter
    private final boolean allowMultipleHolders;

    /**
     * @param requiredBondInBsq     // requiredBond in BSQ for lockup tx
     * @param unlockTimeInDays      // unlockTime in days
     * @param link                  // Link to Github for role description
     * @param allowMultipleHolders  // If role can be held by multiple persons (e.g. seed nodes vs. domain name)
     */
    BondedRoleType(long requiredBondInBsq, int unlockTimeInDays, String link, boolean allowMultipleHolders) {
        this.requiredBond = requiredBondInBsq * 100;
        this.unlockTimeInBlocks = BisqEnvironment.getBaseCurrencyNetwork().isMainnet() ?
                unlockTimeInDays * 144 :    // mainnet (144 blocks per day)
                BisqEnvironment.getBaseCurrencyNetwork().isRegtest() ?
                        5 :                 // regtest (arbitrarily low value for dev testing)
                        144;                // testnet (relatively short time for testing purposes)
        this.link = link;
        this.allowMultipleHolders = allowMultipleHolders;
    }

    public String getDisplayString() {
        return Res.get("dao.bond.bondedRoleType." + name());
    }

    @Override
    public String toString() {
        return "BondedRoleType{" +
                "\n     requiredBond=" + requiredBond +
                ",\n     unlockTime=" + unlockTimeInBlocks +
                ",\n     link='" + link + '\'' +
                ",\n     allowMultipleHolders=" + allowMultipleHolders +
                "\n} " + super.toString();
    }
}
