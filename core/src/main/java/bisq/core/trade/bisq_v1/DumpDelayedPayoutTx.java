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

package bisq.core.trade.bisq_v1;

import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.TradableList;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.JsonUtil;

import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;

import java.util.stream.Collectors;

public class DumpDelayedPayoutTx {
    private final boolean dumpDelayedPayoutTxs;
    private final JsonFileManager jsonFileManager;

    @Inject
    DumpDelayedPayoutTx(@Named(Config.STORAGE_DIR) File storageDir,
                        @Named(Config.DUMP_DELAYED_PAYOUT_TXS) boolean dumpDelayedPayoutTxs) {
        this.dumpDelayedPayoutTxs = dumpDelayedPayoutTxs;
        jsonFileManager = new JsonFileManager(storageDir);
    }

    static class DelayedPayoutHash {
        final String tradeId;
        final String delayedPayoutTx;

        DelayedPayoutHash(String tradeId, String delayedPayoutTx) {
            this.tradeId = tradeId;
            this.delayedPayoutTx = delayedPayoutTx;
        }
    }

    public <T extends Tradable> void maybeDumpDelayedPayoutTxs(TradableList<T> tradableList, String fileName) {
        if (!dumpDelayedPayoutTxs)
            return;

        var delayedPayoutHashes = tradableList.stream()
                .filter(tradable -> tradable instanceof Trade)
                .map(trade -> new DelayedPayoutHash(trade.getId(),
                        Utilities.bytesAsHexString(((Trade) trade).getDelayedPayoutTxBytes())))
                .collect(Collectors.toList());
        jsonFileManager.writeToDiscThreaded(JsonUtil.objectToJson(delayedPayoutHashes), fileName);
    }

}
