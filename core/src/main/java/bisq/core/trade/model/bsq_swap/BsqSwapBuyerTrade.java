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

package bisq.core.trade.model.bsq_swap;

import bisq.core.offer.Offer;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;

import bisq.network.p2p.NodeAddress;

import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class BsqSwapBuyerTrade extends BsqSwapTrade {

    public BsqSwapBuyerTrade(String uid,
                             Offer offer,
                             Coin amount,
                             long takeOfferDate,
                             NodeAddress peerNodeAddress,
                             long txFeePerVbyte,
                             long makerFee,
                             long takerFee,
                             BsqSwapProtocolModel bsqSwapProtocolModel,
                             @Nullable String errorMessage,
                             State state,
                             @Nullable String txId) {
        super(uid,
                offer,
                amount,
                takeOfferDate,
                peerNodeAddress,
                txFeePerVbyte,
                makerFee,
                takerFee,
                bsqSwapProtocolModel,
                errorMessage,
                state,
                txId);
    }
}
