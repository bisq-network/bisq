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

package bisq.core.trade.autoconf.xmr;

import java.util.Date;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
class XmrProofInfo {
    private final String txHash;
    private final String txKey;
    private final String recipientAddress;
    private final long amount;
    private final Date tradeDate;
    private final int confirmsRequired;
    private final String serviceAddress;

    XmrProofInfo(
            String txHash,
            String txKey,
            String recipientAddress,
            long amount,
            Date tradeDate,
            int confirmsRequired,
            String serviceAddress) {
        this.txHash = txHash;
        this.txKey = txKey;
        this.recipientAddress = recipientAddress;
        this.amount = amount;
        this.tradeDate = tradeDate;
        this.confirmsRequired = confirmsRequired;
        this.serviceAddress = serviceAddress;
    }

    String getUID() {
        return txHash + "|" + serviceAddress;
    }
}
