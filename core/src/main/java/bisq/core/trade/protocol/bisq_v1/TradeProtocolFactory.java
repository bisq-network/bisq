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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapBuyerAsMakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapBuyerAsTakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapSellerAsMakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapSellerAsTakerTrade;
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.protocol.bsqswap.BsqSwapBuyerAsMakerProtocol;
import bisq.core.trade.protocol.bsqswap.BsqSwapBuyerAsTakerProtocol;
import bisq.core.trade.protocol.bsqswap.BsqSwapSellerAsMakerProtocol;
import bisq.core.trade.protocol.bsqswap.BsqSwapSellerAsTakerProtocol;

public class TradeProtocolFactory {
    public static TradeProtocol getNewTradeProtocol(TradeModel trade) {
        if (trade instanceof BuyerAsMakerTrade) {
            return new BuyerAsMakerProtocol((BuyerAsMakerTrade) trade);
        } else if (trade instanceof BuyerAsTakerTrade) {
            return new BuyerAsTakerProtocol((BuyerAsTakerTrade) trade);
        } else if (trade instanceof SellerAsMakerTrade) {
            return new SellerAsMakerProtocol((SellerAsMakerTrade) trade);
        } else if (trade instanceof SellerAsTakerTrade) {
            return new SellerAsTakerProtocol((SellerAsTakerTrade) trade);
        } else if (trade instanceof BsqSwapBuyerAsMakerTrade) {
            return new BsqSwapBuyerAsMakerProtocol((BsqSwapBuyerAsMakerTrade) trade);
        } else if (trade instanceof BsqSwapBuyerAsTakerTrade) {
            return new BsqSwapBuyerAsTakerProtocol((BsqSwapBuyerAsTakerTrade) trade);
        } else if (trade instanceof BsqSwapSellerAsMakerTrade) {
            return new BsqSwapSellerAsMakerProtocol((BsqSwapSellerAsMakerTrade) trade);
        } else if (trade instanceof BsqSwapSellerAsTakerTrade) {
            return new BsqSwapSellerAsTakerProtocol((BsqSwapSellerAsTakerTrade) trade);
        } else
            throw new IllegalStateException("Trade not of expected type. Trade=" + trade);
    }
}
