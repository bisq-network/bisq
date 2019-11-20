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

package bisq.desktop.main.dao.burnbsq.proofofburn;

import bisq.desktop.util.DisplayUtils;

import bisq.core.dao.governance.proofofburn.ProofOfBurnService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import java.util.Date;

import lombok.Value;

@Value
class ProofOfBurnListItem {
    private final long amount;
    private final String amountAsString;
    private final String txId;
    private final String hashAsHex;
    private final String pubKey;
    private final Date date;
    private final String dateAsString;

    ProofOfBurnListItem(Tx tx, ProofOfBurnService proofOfBurnService, BsqFormatter bsqFormatter) {
        amount = proofOfBurnService.getAmount(tx);
        amountAsString = bsqFormatter.formatCoinWithCode(Coin.valueOf(amount));
        txId = tx.getId();
        hashAsHex = Utilities.bytesAsHexString(proofOfBurnService.getHashFromOpReturnData(tx));
        pubKey = Utilities.bytesAsHexString(proofOfBurnService.getPubKey(txId));
        date = new Date(tx.getTime());
        dateAsString = DisplayUtils.formatDateTime(date);
    }
}
