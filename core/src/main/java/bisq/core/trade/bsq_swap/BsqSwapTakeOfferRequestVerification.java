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

package bisq.core.trade.bsq_swap;

import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapRequest;
import bisq.core.util.Validator;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.Coin;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.abs;

@Slf4j
public class BsqSwapTakeOfferRequestVerification {

    public static boolean isValid(OpenOfferManager openOfferManager,
                                  FeeService feeService,
                                  KeyRing keyRing,
                                  NodeAddress peer,
                                  BsqSwapRequest request) {
        try {
            log.info("Received {} from {} with tradeId {} and uid {}",
                    request.getClass().getSimpleName(), peer, request.getTradeId(), request.getUid());

            checkNotNull(request);
            Validator.nonEmptyStringOf(request.getTradeId());

            checkArgument(request.getSenderNodeAddress().equals(peer), "Node address not matching");

            Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(request.getTradeId());
            checkArgument(openOfferOptional.isPresent(), "Offer not found in open offers");

            OpenOffer openOffer = openOfferOptional.get();
            checkArgument(openOffer.getState() == OpenOffer.State.AVAILABLE, "Offer not available");

            Offer offer = openOffer.getOffer();
            Validator.checkTradeId(offer.getId(), request);
            checkArgument(offer.isMyOffer(keyRing), "Offer must be mine");

            long tradeAmount = request.getTradeAmount();
            Coin amountAsCoin = Coin.valueOf(request.getTradeAmount());

            checkArgument(tradeAmount >= offer.getMinAmount().getValue() &&
                    tradeAmount <= offer.getAmount().getValue(), "TradeAmount not within offers amount range");
            checkArgument(isDateInTolerance(request), "Trade date is out of tolerance");
            checkArgument(isTxFeeInTolerance(request, feeService), "Miner fee from taker not in tolerance");
            checkArgument(request.getMakerFee() == Objects.requireNonNull(CoinUtil.getMakerFee(false, amountAsCoin)).value);
            checkArgument(request.getTakerFee() == Objects.requireNonNull(CoinUtil.getTakerFee(false, amountAsCoin)).value);
        } catch (Exception e) {
            log.error("BsqSwapTakeOfferRequestVerification failed. Request={}, peer={}, error={}", request, peer, e.toString());
            return false;
        }

        return true;
    }

    private static boolean isDateInTolerance(BsqSwapRequest request) {
        return abs(request.getTradeDate() - new Date().getTime()) < TimeUnit.MINUTES.toMillis(10);
    }

    private static boolean isTxFeeInTolerance(BsqSwapRequest request, FeeService feeService) {
        double myFee = (double) feeService.getTxFeePerVbyte().getValue();
        double peersFee = (double) Coin.valueOf(request.getTxFeePerVbyte()).getValue();
        // Allow for 50% diff in mining fee, ie, maker will accept taker fee that's less
        // than 50% off their own fee from service (that is, 100% higher or 50% lower).
        // Both parties will use the same fee while creating the bsq swap tx.
        double diff = abs(1 - myFee / peersFee);
        boolean isInTolerance = diff < 0.5;
        if (!isInTolerance) {
            log.warn("Miner fee from taker not in tolerance. myFee={}, peersFee={}, diff={}", myFee, peersFee, diff);
        }
        return isInTolerance;
    }
}
