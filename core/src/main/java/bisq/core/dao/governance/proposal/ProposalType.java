/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.proposal;

import bisq.core.locale.Res;

public enum ProposalType {
    UNDEFINED,
    COMPENSATION_REQUEST,
    REIMBURSEMENT_REQUEST,
    CHANGE_PARAM,
    BONDED_ROLE,
    CONFISCATE_BOND,
    GENERIC,
    REMOVE_ASSET;

    public String getDisplayName() {
        return Res.get("dao.proposal.type." + name());
    }

    public String getShortDisplayName() {
        return Res.get("dao.proposal.type.short." + name());
    }
}
