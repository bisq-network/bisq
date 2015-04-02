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

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.trade.BuyerAsOffererTrade;
import io.bitsquare.trade.BuyerAsTakerTrade;
import io.bitsquare.trade.SellerAsOffererTrade;
import io.bitsquare.trade.SellerAsTakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.states.OffererState;
import io.bitsquare.trade.states.TakerState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateUtil {
    private static final Logger log = LoggerFactory.getLogger(StateUtil.class);

    public static void setSendFailedState(Trade trade) {
        if (trade instanceof BuyerAsOffererTrade || trade instanceof SellerAsOffererTrade)
            trade.setProcessState(OffererState.ProcessState.MESSAGE_SENDING_FAILED);
        else if (trade instanceof BuyerAsTakerTrade || trade instanceof SellerAsTakerTrade)
            trade.setProcessState(TakerState.ProcessState.MESSAGE_SENDING_FAILED);
    }

    public static void setOfferOpenState(Trade trade) {
        if (trade instanceof BuyerAsOffererTrade || trade instanceof SellerAsOffererTrade)
            trade.setLifeCycleState(OffererState.LifeCycleState.OFFER_OPEN);
    }
} 
