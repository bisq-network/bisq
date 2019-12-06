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

package bisq.desktop.main.dao.bonding.bonds;

import bisq.desktop.util.DisplayUtils;

import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import java.util.Date;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Slf4j
class BondListItem {
    private final Bond bond;
    private final String bondType;
    private final String lockupTxId;
    private final String amount;
    private final String lockupDateString;
    private final String lockTime;
    private final String bondDetails;
    private final BondState bondState;
    private final String bondStateString;

    BondListItem(Bond bond, BsqFormatter bsqFormatter) {
        this.bond = bond;

        amount = bsqFormatter.formatCoin(Coin.valueOf(bond.getAmount()));
        lockTime = Integer.toString(bond.getLockTime());
        if (bond instanceof BondedRole) {
            bondType = Res.get("dao.bond.bondedRoles");
            bondDetails = bond.getBondedAsset().getDisplayString();
        } else {
            bondType = Res.get("dao.bond.bondedReputation");
            bondDetails = Utilities.bytesAsHexString(bond.getBondedAsset().getHash());
        }
        lockupTxId = bond.getLockupTxId();
        lockupDateString = bond.getLockupDate() > 0 ? DisplayUtils.formatDateTime(new Date(bond.getLockupDate())) : "-";
        bondState = bond.getBondState();
        bondStateString = Res.get("dao.bond.bondState." + bond.getBondState().name());
    }
}
