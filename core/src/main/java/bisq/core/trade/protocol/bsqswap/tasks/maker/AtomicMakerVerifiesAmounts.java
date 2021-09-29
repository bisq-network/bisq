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

package bisq.core.trade.protocol.bsqswap.tasks.maker;

import bisq.core.trade.messages.bsqswap.CreateAtomicTxRequest;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.AtomicSetupTxListener;
import bisq.core.util.Validator;
import bisq.core.util.coin.CoinUtil;

import bisq.common.taskrunner.TaskRunner;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AtomicMakerVerifiesAmounts extends AtomicSetupTxListener {

    @SuppressWarnings({"unused"})
    public AtomicMakerVerifiesAmounts(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();


            // AtomicTrade is initialized with values from CreateAtomicTxRequest on creation
            // Verify that AtomicTrade values don't disagree with the offer

            // Verify offer and message
            Validator.checkTradeId(bsqSwapProtocolModel.getOffer().getId(), bsqSwapProtocolModel.getTradeMessage());
            checkArgument(bsqSwapProtocolModel.getOffer().isMyOffer(bsqSwapProtocolModel.getKeyRing()),
                    "must only process own offer");
            checkArgument(bsqSwapProtocolModel.getTradeMessage() instanceof CreateAtomicTxRequest,
                    "Expected CreateAtomicTxRequest");


            var message = (CreateAtomicTxRequest) bsqSwapProtocolModel.getTradeMessage();
            var offer = bsqSwapTrade.getOffer();
            bsqSwapProtocolModel.initFromTrade(bsqSwapTrade);
            bsqSwapProtocolModel.setMakerBsqAddress(
                    bsqSwapProtocolModel.getBsqWalletService().getUnusedAddress().toString());
            bsqSwapProtocolModel.setMakerBtcAddress(
                    bsqSwapProtocolModel.getBtcWalletService().getFreshAddressEntry().getAddressString());

            checkArgument(bsqSwapTrade.getPrice().equals(offer.getPrice()),
                    "Trade price does not match offer");
            checkArgument(bsqSwapTrade.getAmount().getValue() >= offer.getMinAmount().getValue() &&
                            bsqSwapTrade.getAmount().getValue() <= offer.getAmount().getValue(),
                    "btcTradeAmount not within range");
            checkArgument(message.getBsqTradeAmount() >= bsqSwapProtocolModel.getBsqMinTradeAmount() &&
                            message.getBsqTradeAmount() <= bsqSwapProtocolModel.getBsqMaxTradeAmount(),
                    "bsqTradeAmount not within range");
            checkArgument(bsqSwapTrade.getMakerFee() ==
                            Objects.requireNonNull(CoinUtil.getMakerFee(false, bsqSwapTrade.getAmount())).getValue(),
                    "Maker fee mismatch");
            checkArgument(bsqSwapTrade.getTakerFee() ==
                            Objects.requireNonNull(CoinUtil.getTakerFee(false, bsqSwapTrade.getAmount())).getValue(),
                    "Taker fee mismatch");

            bsqSwapProtocolModel.updateFromMessage(message);
            bsqSwapProtocolModel.initTxBuilder(true);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
