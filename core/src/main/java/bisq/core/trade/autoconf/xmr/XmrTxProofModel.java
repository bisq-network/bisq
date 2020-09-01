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
public class XmrTxProofModel {
    // Those are values from a valid tx which are set automatically if DevEnv.isDevMode is enabled
    public static final String DEV_ADDRESS = "85q13WDADXE26W6h7cStpPMkn8tWpvWgHbpGWWttFEafGXyjsBTXxxyQms4UErouTY5sdKpYHVjQm6SagiCqytseDkzfgub";
    public static final String DEV_TX_KEY = "f3ce66c9d395e5e460c8802b2c3c1fff04e508434f9738ee35558aac4678c906";
    public static final String DEV_TX_HASH = "5e665addf6d7c6300670e8a89564ed12b5c1a21c336408e2835668f9a6a0d802";
    public static final long DEV_AMOUNT = 8902597360000L;

    private final String tradeId;
    private final String txHash;
    private final String txKey;
    private final String recipientAddress;
    private final long amount;
    private final Date tradeDate;
    private final int confirmsRequired;
    private final String serviceAddress;

    XmrTxProofModel(
            String tradeId,
            String txHash,
            String txKey,
            String recipientAddress,
            long amount,
            Date tradeDate,
            int confirmsRequired,
            String serviceAddress) {
        this.tradeId = tradeId;
        this.txHash = txHash;
        this.txKey = txKey;
        this.recipientAddress = recipientAddress;
        this.amount = amount;
        this.tradeDate = tradeDate;
        this.confirmsRequired = confirmsRequired;
        this.serviceAddress = serviceAddress;
    }
}
