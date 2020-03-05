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

package bisq.core.trade;

import bisq.common.storage.JsonFileManager;
import bisq.common.util.Utilities;

import java.util.stream.Collectors;

class DumpDelayedPayoutTx {

    static class DelayedPayoutHash {
        String tradeId;
        String delayedPayoutTx;
        DelayedPayoutHash(String tradeId, String delayedPayoutTx) {
            this.tradeId = tradeId;
            this.delayedPayoutTx = delayedPayoutTx;
        }
    }

    static void dumpDelayedPayoutTxs(TradableList<Trade> tradableList, JsonFileManager jsonFileManager,
                                     String fileName) {
        var delayedPayoutHashes = tradableList.stream()
                .map(trade -> new DelayedPayoutHash(trade.getId(),
                        Utilities.bytesAsHexString(trade.getDelayedPayoutTxBytes())))
                .collect(Collectors.toList());
        jsonFileManager.writeToDisc(Utilities.objectToJson(delayedPayoutHashes), fileName);
    }

}
