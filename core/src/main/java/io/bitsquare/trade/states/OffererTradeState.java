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

public class OffererTradeState {
    private static final Logger log = LoggerFactory.getLogger(OffererTradeState.class);

    public enum LifeCycleState implements TradeState.LifeCycleState {
        OFFER_OPEN,
        OFFER_RESERVED,
        OFFER_CANCELED,
        PENDING,
        COMPLETED,
        FAILED
    }

    public enum ProcessState implements TradeState.ProcessState {
        UNDEFINED,
        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,

        FIAT_PAYMENT_STARTED,

        FIAT_PAYMENT_RECEIVED,
        PAYOUT_PUBLISHED,

        MESSAGE_SENDING_FAILED,
        EXCEPTION
    }
}
