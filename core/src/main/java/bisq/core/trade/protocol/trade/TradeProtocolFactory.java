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

package bisq.core.trade.protocol.trade;

import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bsqswap.BsqSwapMakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapTakerTrade;
import bisq.core.trade.model.trade.BuyerAsMakerTrade;
import bisq.core.trade.model.trade.BuyerAsTakerTrade;
import bisq.core.trade.model.trade.SellerAsMakerTrade;
import bisq.core.trade.model.trade.SellerAsTakerTrade;
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.protocol.bsqswap.BsqSwapMakerProtocol;
import bisq.core.trade.protocol.bsqswap.BsqSwapTakerProtocol;

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
        } else if (trade instanceof BsqSwapTakerTrade) {
            return new BsqSwapTakerProtocol((BsqSwapTakerTrade) trade);
        } else if (trade instanceof BsqSwapMakerTrade) {
            return new BsqSwapMakerProtocol((BsqSwapMakerTrade) trade);
        } else
            throw new IllegalStateException("Trade not of expected type. Trade=" + trade);
    }
}
