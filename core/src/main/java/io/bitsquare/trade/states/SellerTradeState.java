/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.states;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerTradeState {
    private static final Logger log = LoggerFactory.getLogger(SellerTradeState.class);

    public enum ProcessState implements TradeState.ProcessState {
        UNDEFINED,

        DEPOSIT_PUBLISHED_MSG_RECEIVED,
        DEPOSIT_CONFIRMED,

        FIAT_PAYMENT_STARTED_MSG_RECEIVED,

        FIAT_PAYMENT_RECEIPT,
        FIAT_PAYMENT_RECEIPT_MSG_SENT,

        PAYOUT_TX_RECEIVED,
        PAYOUT_TX_COMMITTED,

        PAYOUT_BROAD_CASTED
    }
}
