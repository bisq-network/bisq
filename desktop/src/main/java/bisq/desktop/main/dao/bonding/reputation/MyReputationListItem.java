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

package bisq.desktop.main.dao.bonding.reputation;

import bisq.desktop.util.DisplayUtils;

import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.reputation.MyBondedReputation;
import bisq.core.dao.governance.bond.reputation.MyReputation;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import java.util.Date;

import lombok.Value;

@Value
class MyReputationListItem {
    private final MyBondedReputation myBondedReputation;
    private final String hash, salt;
    private final String txId;
    private final String amount;
    private final String lockupDateString;
    private final String lockTime;

    private final String buttonText;
    private final boolean showButton;
    private final BondState bondState;
    private final String bondStateString;
    private final String lockupTxId;
    private final Date lockupDate;

    MyReputationListItem(MyBondedReputation myBondedReputation,
                         BsqFormatter bsqFormatter) {
        this.myBondedReputation = myBondedReputation;

        MyReputation myReputation = myBondedReputation.getBondedAsset();
        hash = Utilities.bytesAsHexString(myReputation.getHash());
        salt = Utilities.bytesAsHexString(myReputation.getSalt());
        txId = myBondedReputation.getLockupTxId();
        amount = bsqFormatter.formatCoin(Coin.valueOf(myBondedReputation.getAmount()));
        lockupDate = new Date(myBondedReputation.getLockupDate());
        lockupDateString = DisplayUtils.formatDateTime(lockupDate);
        lockTime = Integer.toString(myBondedReputation.getLockTime());
        lockupTxId = myBondedReputation.getLockupTxId();
        bondState = myBondedReputation.getBondState();
        bondStateString = Res.get("dao.bond.bondState." + myBondedReputation.getBondState().name());
        showButton = myBondedReputation.getBondState() == BondState.LOCKUP_TX_CONFIRMED;
        buttonText = Res.get("dao.bond.table.button.unlock");
    }
}
