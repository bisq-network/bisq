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

package bisq.core.trade.atomic.protocol.tasks.maker;

import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.messages.CreateAtomicTxRequest;
import bisq.core.trade.atomic.protocol.tasks.AtomicSetupTxListener;
import bisq.core.util.Validator;
import bisq.core.util.coin.CoinUtil;

import bisq.common.taskrunner.TaskRunner;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AtomicMakerVerifiesAmounts extends AtomicSetupTxListener {

    @SuppressWarnings({"unused"})
    public AtomicMakerVerifiesAmounts(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();


            // AtomicTrade is initialized with values from CreateAtomicTxRequest on creation
            // Verify that AtomicTrade values don't disagree with the offer

            // Verify offer and message
            Validator.checkTradeId(atomicProcessModel.getOffer().getId(), atomicProcessModel.getTradeMessage());
            checkArgument(atomicProcessModel.getOffer().isMyOffer(atomicProcessModel.getKeyRing()),
                    "must only process own offer");
            checkArgument(atomicProcessModel.getTradeMessage() instanceof CreateAtomicTxRequest,
                    "Expected CreateAtomicTxRequest");


            var message = (CreateAtomicTxRequest) atomicProcessModel.getTradeMessage();
            var offer = atomicTrade.getOffer();
            atomicProcessModel.initFromTrade(atomicTrade);
            atomicProcessModel.setMakerBsqAddress(
                    atomicProcessModel.getBsqWalletService().getUnusedAddress().toString());
            atomicProcessModel.setMakerBtcAddress(
                    atomicProcessModel.getBtcWalletService().getFreshAddressEntry().getAddressString());

            checkArgument(atomicTrade.getPrice().equals(offer.getPrice()),
                    "Trade price does not match offer");
            checkArgument(atomicTrade.getAmount().getValue() >= offer.getMinAmount().getValue() &&
                            atomicTrade.getAmount().getValue() <= offer.getAmount().getValue(),
                    "btcTradeAmount not within range");
            checkArgument(message.getBsqTradeAmount() >= atomicProcessModel.getBsqMinTradeAmount() &&
                            message.getBsqTradeAmount() <= atomicProcessModel.getBsqMaxTradeAmount(),
                    "bsqTradeAmount not within range");
            checkArgument(atomicTrade.isCurrencyForMakerFeeBtc() == offer.isCurrencyForMakerFeeBtc(),
                    "Maker fee type mismatch");
            checkArgument(atomicTrade.getMakerFee() ==
                            Objects.requireNonNull(CoinUtil.getMakerFee(offer.isCurrencyForMakerFeeBtc(),
                                    atomicTrade.getAmount())).getValue(),
                    "Maker fee mismatch");
            checkArgument(atomicTrade.getTakerFee() ==
                            Objects.requireNonNull(CoinUtil.getTakerFee(atomicTrade.isCurrencyForTakerFeeBtc(),
                                    atomicTrade.getAmount())).getValue(),
                    "Taker fee mismatch");

            atomicProcessModel.updateFromMessage(message);
            atomicProcessModel.initTxBuilder(true);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
