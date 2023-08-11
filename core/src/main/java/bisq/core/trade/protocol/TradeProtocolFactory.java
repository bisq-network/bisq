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

package bisq.core.trade.protocol;

import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapBuyerAsMakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapBuyerAsTakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapSellerAsMakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapSellerAsTakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bisq_v1.protocol_v4.BuyerAsMakerProtocol_v4;
import bisq.core.trade.protocol.bisq_v1.protocol_v4.BuyerAsTakerProtocol_v4;
import bisq.core.trade.protocol.bisq_v1.protocol_v4.SellerAsMakerProtocol_v4;
import bisq.core.trade.protocol.bisq_v1.protocol_v4.SellerAsTakerProtocol_v4;
import bisq.core.trade.protocol.bisq_v5.BuyerAsMakerProtocol_v5;
import bisq.core.trade.protocol.bisq_v5.BuyerAsTakerProtocol_v5;
import bisq.core.trade.protocol.bisq_v5.SellerAsMakerProtocol_v5;
import bisq.core.trade.protocol.bisq_v5.SellerAsTakerProtocol_v5;
import bisq.core.trade.protocol.bsq_swap.BsqSwapBuyerAsMakerProtocol;
import bisq.core.trade.protocol.bsq_swap.BsqSwapBuyerAsTakerProtocol;
import bisq.core.trade.protocol.bsq_swap.BsqSwapSellerAsMakerProtocol;
import bisq.core.trade.protocol.bsq_swap.BsqSwapSellerAsTakerProtocol;

import bisq.common.app.Version;

public class TradeProtocolFactory {
    public static TradeProtocol getNewTradeProtocol(TradeModel tradeModel) {
        if (tradeModel instanceof BsqSwapTrade) {
            if (tradeModel instanceof BsqSwapBuyerAsMakerTrade) {
                return new BsqSwapBuyerAsMakerProtocol((BsqSwapBuyerAsMakerTrade) tradeModel);
            } else if (tradeModel instanceof BsqSwapBuyerAsTakerTrade) {
                return new BsqSwapBuyerAsTakerProtocol((BsqSwapBuyerAsTakerTrade) tradeModel);
            } else if (tradeModel instanceof BsqSwapSellerAsMakerTrade) {
                return new BsqSwapSellerAsMakerProtocol((BsqSwapSellerAsMakerTrade) tradeModel);
            } else if (tradeModel instanceof BsqSwapSellerAsTakerTrade) {
                return new BsqSwapSellerAsTakerProtocol((BsqSwapSellerAsTakerTrade) tradeModel);
            }
        } else {
            boolean tradeProtocolVersion5Activated = Version.isTradeProtocolVersion5Activated();
            if (tradeModel instanceof BuyerAsMakerTrade) {
                return tradeProtocolVersion5Activated ?
                        new BuyerAsMakerProtocol_v5((BuyerAsMakerTrade) tradeModel) :
                        new BuyerAsMakerProtocol_v4((BuyerAsMakerTrade) tradeModel);
            } else if (tradeModel instanceof BuyerAsTakerTrade) {
                return tradeProtocolVersion5Activated ?
                        new BuyerAsTakerProtocol_v5((BuyerAsTakerTrade) tradeModel) :
                        new BuyerAsTakerProtocol_v4((BuyerAsTakerTrade) tradeModel);
            } else if (tradeModel instanceof SellerAsMakerTrade) {
                return tradeProtocolVersion5Activated ?
                        new SellerAsMakerProtocol_v5((SellerAsMakerTrade) tradeModel) :
                        new SellerAsMakerProtocol_v4((SellerAsMakerTrade) tradeModel);
            } else if (tradeModel instanceof SellerAsTakerTrade) {
                return tradeProtocolVersion5Activated ?
                        new SellerAsTakerProtocol_v5((SellerAsTakerTrade) tradeModel) :
                        new SellerAsTakerProtocol_v4((SellerAsTakerTrade) tradeModel);
            }
        }

        throw new IllegalStateException("Trade not of expected type. Trade=" + tradeModel);
    }
}
