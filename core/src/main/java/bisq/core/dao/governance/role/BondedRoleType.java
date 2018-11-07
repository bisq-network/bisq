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

package bisq.core.dao.governance.role;

import bisq.core.locale.Res;

import lombok.Getter;

// Data here must not be changed as it would break backward compatibility! In case we need to change we need to add a new
// entry and maintain the old one. Once all the role holders of an old deprecated role have revoked the role might get removed.
public enum BondedRoleType {
    ARBITRATOR(10000000, 5, "https://github.com/bisq-network/roles/issues/13", true), // 100 000 BSQ, 30 days unlock time
    DOMAIN_NAME_HOLDER(5000000, 20, "https://github.com/bisq-network/roles/issues/15", false), // 50 000 BSQ, 20 days unlock time //TODO no link yet
    SEED_NODE_OPERATOR(2000000, 50, "https://github.com/bisq-network/roles/issues/15", true); // 20 000 BSQ, 30 days unlock time
/*
    ARBITRATOR(10000000, 144 * 30, "https://github.com/bisq-network/roles/issues/13", true), // 100 000 BSQ, 30 days unlock time
    DOMAIN_NAME_HOLDER(5000000, 144 * 20, "https://github.com/bisq-network/roles/issues/15", false), // 50 000 BSQ, 20 days unlock time //TODO no link yet
    SEED_NODE_OPERATOR(2000000, 144 * 30, "https://github.com/bisq-network/roles/issues/15", true); // 20 000 BSQ, 30 days unlock time
*/


    @Getter
    private final long requiredBond;
    @Getter
    private final int unlockTime;
    @Getter
    private final String link;
    @Getter
    private final boolean allowMultipleHolders;

    /**
     *
     * @param requiredBond          // requiredBond in BSQ for lockup tx
     * @param unlockTime            // unlockTime in blocks
     * @param link                  // Link to Github for role description
     * @param allowMultipleHolders  // If role can be held by multiple persons (e.g. seed nodes in contrary to domain name)
     */
    BondedRoleType(long requiredBond, int unlockTime, String link, boolean allowMultipleHolders) {
        this.requiredBond = requiredBond;
        this.unlockTime = unlockTime;
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
                ",\n     unlockTime=" + unlockTime +
                ",\n     link='" + link + '\'' +
                ",\n     allowMultipleHolders=" + allowMultipleHolders +
                "\n} " + super.toString();
    }
}
