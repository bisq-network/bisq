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
import bisq.core.trade.protocol.bisq_v1.BuyerAsMakerProtocol;
import bisq.core.trade.protocol.bisq_v1.BuyerAsTakerProtocol;
import bisq.core.trade.protocol.bisq_v1.SellerAsMakerProtocol;
import bisq.core.trade.protocol.bisq_v1.SellerAsTakerProtocol;
import bisq.core.trade.protocol.bsq_swap.BsqSwapBuyerAsMakerProtocol;
import bisq.core.trade.protocol.bsq_swap.BsqSwapBuyerAsTakerProtocol;
import bisq.core.trade.protocol.bsq_swap.BsqSwapSellerAsMakerProtocol;
import bisq.core.trade.protocol.bsq_swap.BsqSwapSellerAsTakerProtocol;

public class TradeProtocolFactory {
    public static TradeProtocol getNewTradeProtocol(TradeModel tradeModel) {
        if (tradeModel instanceof BuyerAsMakerTrade) {
            return new BuyerAsMakerProtocol((BuyerAsMakerTrade) tradeModel);
        } else if (tradeModel instanceof BuyerAsTakerTrade) {
            return new BuyerAsTakerProtocol((BuyerAsTakerTrade) tradeModel);
        } else if (tradeModel instanceof SellerAsMakerTrade) {
            return new SellerAsMakerProtocol((SellerAsMakerTrade) tradeModel);
        } else if (tradeModel instanceof SellerAsTakerTrade) {
            return new SellerAsTakerProtocol((SellerAsTakerTrade) tradeModel);
        } else if (tradeModel instanceof BsqSwapBuyerAsMakerTrade) {
            return new BsqSwapBuyerAsMakerProtocol((BsqSwapBuyerAsMakerTrade) tradeModel);
        } else if (tradeModel instanceof BsqSwapBuyerAsTakerTrade) {
            return new BsqSwapBuyerAsTakerProtocol((BsqSwapBuyerAsTakerTrade) tradeModel);
        } else if (tradeModel instanceof BsqSwapSellerAsMakerTrade) {
            return new BsqSwapSellerAsMakerProtocol((BsqSwapSellerAsMakerTrade) tradeModel);
        } else if (tradeModel instanceof BsqSwapSellerAsTakerTrade) {
            return new BsqSwapSellerAsTakerProtocol((BsqSwapSellerAsTakerTrade) tradeModel);
        } else
            throw new IllegalStateException("Trade not of expected type. Trade=" + tradeModel);
    }
}
