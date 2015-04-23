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

import io.bitsquare.trade.Trade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateUtil {
    private static final Logger log = LoggerFactory.getLogger(StateUtil.class);

    // TODO remove?
    public static void setSendFailedState(Trade trade) {
       /* if (trade instanceof BuyerTrade)
            trade.setProcessState(BuyerProcessState.MESSAGE_SENDING_FAILED);
        else if (trade instanceof SellerTrade)
            trade.setProcessState(SellerProcessState.MESSAGE_SENDING_FAILED);*/
    }

    // TODO remove?
    public static void setOfferOpenState(Trade trade) {
        /*if (trade instanceof BuyerTrade)
            trade.setLifeCycleState(Trade.LifeCycleState.PREPARATION);*/
    }
} 
