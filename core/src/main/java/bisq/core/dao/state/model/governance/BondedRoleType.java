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

import bisq.core.locale.Res;

import bisq.common.config.Config;

import lombok.Getter;


/**
 * Data here must not be changed as it would break backward compatibility! In case we need to change we need to add a
 * new entry and maintain the old one. Once all the role holders of an old deprecated role have revoked the
 * role might get removed.
 *
 * Add entry to translation file "dao.bond.bondedRoleType...."
 *
 * Name of the BondedRoleType must not change as that is used for serialisation in Protobuffer. The data fields are not part of
 * the PB serialisation so changes for those would not change the hash for the dao state hash chain.
 * As the data is not used in consensus critical code yet changing fields can be tolerated.
 * For mediators and arbitrators we will use automated verification of the bond so there might be issues when we change
 * the values. So let's avoid changing anything here beside adding new entries.
 *
 */
public enum BondedRoleType {
    UNDEFINED(0, 0, "N/A", false),
    // admins
    GITHUB_ADMIN(50, 110, "https://bisq.network/roles/16", true),
    FORUM_ADMIN(20, 110, "https://bisq.network/roles/19", true),
    TWITTER_ADMIN(20, 110, "https://bisq.network/roles/21", true),
    ROCKET_CHAT_ADMIN(20, 110, "https://bisq.network/roles/79", true),
    YOUTUBE_ADMIN(10, 110, "https://bisq.network/roles/56", true),

    // maintainers
    BISQ_MAINTAINER(50, 110, "https://bisq.network/roles/63", true),
    BITCOINJ_MAINTAINER(20, 110, "https://bisq.network/roles/8", true),
    NETLAYER_MAINTAINER(20, 110, "https://bisq.network/roles/81", true),

    // operators
    WEBSITE_OPERATOR(50, 110, "https://bisq.network/roles/12", true),
    FORUM_OPERATOR(50, 110, "https://bisq.network/roles/19", true),
    SEED_NODE_OPERATOR(20, 110, "https://bisq.network/roles/15", true),
    DATA_RELAY_NODE_OPERATOR(20, 110, "https://bisq.network/roles/14", true),
    BTC_NODE_OPERATOR(5, 110, "https://bisq.network/roles/67", true),
    MARKETS_OPERATOR(20, 110, "https://bisq.network/roles/9", true),
    BSQ_EXPLORER_OPERATOR(20, 110, "https://bisq.network/roles/11", true),
    MOBILE_NOTIFICATIONS_RELAY_OPERATOR(20, 110, "https://bisq.network/roles/82", true),

    // other
    DOMAIN_NAME_HOLDER(50, 110, "https://bisq.network/roles/77", false),
    DNS_ADMIN(20, 110, "https://bisq.network/roles/18", false),
    MEDIATOR(10, 110, "https://bisq.network/roles/83", true),
    ARBITRATOR(200, 110, "https://bisq.network/roles/13", true),
    BTC_DONATION_ADDRESS_OWNER(50, 110, "https://bisq.network/roles/80", true);


    // Will be multiplied with PARAM.BONDED_ROLE_FACTOR to get BSQ amount.
    // As BSQ is volatile we need to adjust the bonds over time.
    // To avoid changing the Enum we use the BONDED_ROLE_FACTOR param to react on BSQ price changes.
    // Required bond = requiredBondUnit * PARAM.BONDED_ROLE_FACTOR.value
    @Getter
    private final long requiredBondUnit;

    // Unlock time in blocks
    @Getter
    private final int unlockTimeInBlocks;
    @Getter
    private final String link;
    @Getter
    private final boolean allowMultipleHolders;

    /**
     * @param requiredBondUnit          // requiredBondUnit for lockup tx (will be multiplied with PARAM.BONDED_ROLE_FACTOR for BSQ value)
     * @param unlockTimeInDays          // unlockTime in days
     * @param link                      // Link to GitHub for role description
     * @param allowMultipleHolders      // If role can be held by multiple persons (e.g. seed nodes vs. domain name)
     */
    BondedRoleType(long requiredBondUnit, int unlockTimeInDays, String link, boolean allowMultipleHolders) {
        this.requiredBondUnit = requiredBondUnit;
        this.unlockTimeInBlocks = Config.baseCurrencyNetwork().isMainnet() ?
                unlockTimeInDays * 144 :    // mainnet (144 blocks per day)
                Config.baseCurrencyNetwork().isRegtest() ?
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
                "\n     requiredBondUnit=" + requiredBondUnit +
                ",\n     unlockTime=" + unlockTimeInBlocks +
                ",\n     link='" + link + '\'' +
                ",\n     allowMultipleHolders=" + allowMultipleHolders +
                "\n} " + super.toString();
    }
}
