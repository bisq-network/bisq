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

package bisq.core.trade.protocol.tasks.maker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.offer.Offer;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CreateAtomicTxRequest;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionInput;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AtomicMakerVerifiesTakerInputs extends TradeTask {
    @SuppressWarnings({"unused"})
    public AtomicMakerVerifiesTakerInputs(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            /* Tx format:
             * [1]     (BSQtradeAmount, makerBSQAddress)
             * [0-1]   (BSQchange, takerBSQAddress)  (Change from BSQ for tradeAmount and/or tradeFee)
             * [1]     (BTCtradeAmount + BTCchange, takerBTCAddress) (Change from BTC for txFee and/or tradeFee)
             * [0-1]   (BTCchange, makerBTCAddress) (Change from BTC for tradeAmount payment)
             * [0-1]   (BTCtradeFee)
             */

            checkArgument(processModel.getOffer().isMyOffer(processModel.getKeyRing()), "must process own offer");
            var isBuyer = processModel.getOffer().isBuyOffer();

            if (!(processModel.getTradeMessage() instanceof CreateAtomicTxRequest))
                failed("Expected CreateAtomicTxRequest");

            var message = (CreateAtomicTxRequest) processModel.getTradeMessage();
            var atomicModel = checkNotNull(processModel.getAtomicModel(), "AtomicModel must not be null");
            atomicModel.updateFromCreateAtomicTxRequest(message);

            Offer offer = checkNotNull(trade.getOffer(), "Offer must not be null");
            if (message.getTradePrice() != atomicModel.getTradePrice())
                failed("Unexpected trade price");
            trade.setTradePrice(message.getTradePrice());
            processModel.getTradingPeer().setPubKeyRing(checkNotNull(message.getTakerPubKeyRing()));
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());
            trade.setTradeAmount(Coin.valueOf(message.getBtcTradeAmount()));

            if (message.getBsqTradeAmount() < atomicModel.getBsqMinTradeAmount() ||
                    message.getBsqTradeAmount() > atomicModel.getBsqMaxTradeAmount())
                failed("bsqTradeAmount not within range");
            if (message.getBtcTradeAmount() < atomicModel.getBtcMinTradeAmount() ||
                    message.getBtcTradeAmount() > atomicModel.getBtcMaxTradeAmount())
                failed("btcTradeAmount not within range");
            if (message.getTakerFee() !=
                    (message.isCurrencyForTakerFeeBtc() ? atomicModel.getBtcTradeFee() : atomicModel.getBsqTradeFee()))
                failed("Taker fee mismatch");
            var bsqAmount = atomicModel.bsqAmountFromVolume(trade.getTradeVolume()).orElse(null);
            if (bsqAmount == null || bsqAmount != message.getBsqTradeAmount())
                failed("Amounts don't match price");
            // TODO verify txFee is reasonable

            // Verify taker bsq address
            processModel.getBsqWalletService().getBsqFormatter().getAddressFromBsqAddress(
                    message.getTakerBsqOutputAddress());

            var makerAddress = processModel.getBtcWalletService().getOrCreateAddressEntry(
                    trade.getId(), AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            List<TransactionInput> makerBtcInputs = new ArrayList<>();
            if (!isBuyer) {
                makerBtcInputs = processModel.getTradeWalletService().getInputsForAddress(makerAddress,
                        trade.getTradeAmount());
            }
            // Inputs
            var makerBsqInputAmount = 0L;
            var makerBsqOutputAmount = message.getBsqTradeAmount();
            if (isBuyer) {
                // TODO Reserve BSQ for trade
                // Prepare BSQ as it's not part of the prepared tx
                var requiredBsq = atomicModel.getBsqTradeAmount();
                var preparedBsq = processModel.getBsqWalletService().prepareAtomicBsqInputs(Coin.valueOf(requiredBsq));
                makerBsqInputAmount = processModel.getBsqWalletService().getBsqInputAmount(
                        preparedBsq.first.getInputs(), processModel.getDaoFacade());
                makerBsqOutputAmount = preparedBsq.second.getValue();
                atomicModel.setMakerBsqInputs(preparedBsq.first.getInputs());
            }

            var takerBsqInputAmount = processModel.getBsqWalletService().getBsqRawInputAmount(
                    message.getTakerBsqInputs(), processModel.getDaoFacade());
            var takerBtcInputAmount = processModel.getBtcWalletService().getBtcRawInputAmount(
                    message.getTakerBtcInputs());
            var makerBtcInputAmount = processModel.getBtcWalletService().getBtcInputAmount(makerBtcInputs);

            // Outputs and fees
            var takerBsqOutputAmount = message.getTakerBsqOutputValue();
            var takerBtcOutputAmount = message.getTakerBtcOutputValue();
            var makerBtcOutputAmount = isBuyer ? message.getBtcTradeAmount() :
                    makerBtcInputAmount - message.getBtcTradeAmount();
            var btcTradeFeeAmount = atomicModel.getBtcTradeFee();
            var bsqTradeFee = atomicModel.getBsqTradeFee();
            var txFee = message.getTxFee();

            // Verify input sum equals output sum
            var bsqIn = takerBsqInputAmount + makerBsqInputAmount;
            var bsqOut = takerBsqOutputAmount + makerBsqOutputAmount;
            if (bsqIn != bsqOut + bsqTradeFee)
                failed("BSQ in does not match BSQ out");
            var btcIn = takerBtcInputAmount + makerBtcInputAmount + bsqTradeFee;
            var btcOut = takerBtcOutputAmount + makerBtcOutputAmount + btcTradeFeeAmount;
            if (btcIn != btcOut + txFee)
                failed("BTC in does not match BTC out");

            // Message data is verified as correct, update model with data from message
            atomicModel.setMakerBtcInputs(makerBtcInputs);
            atomicModel.setMakerBtcOutputAmount(makerBtcOutputAmount);
            atomicModel.setMakerBsqOutputAmount(makerBsqOutputAmount);
            atomicModel.setTxFee(message.getTxFee());

            trade.persist();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
