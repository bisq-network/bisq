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

import bisq.core.btc.model.AddressEntry;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.messages.CreateAtomicTxRequest;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;
import bisq.core.util.Validator;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionInput;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AtomicMakerVerifiesTakerInputs extends AtomicTradeTask {
    @SuppressWarnings({"unused"})
    public AtomicMakerVerifiesTakerInputs(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
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

            Validator.checkTradeId(atomicProcessModel.getOffer().getId(), atomicProcessModel.getTradeMessage());

            checkArgument(atomicProcessModel.getOffer().isMyOffer(atomicProcessModel.getKeyRing()),
                    "must process own offer");
            var isBuyer = atomicProcessModel.getOffer().isBuyOffer();

            if (!(atomicProcessModel.getTradeMessage() instanceof CreateAtomicTxRequest))
                failed("Expected CreateAtomicTxRequest");

            var message = (CreateAtomicTxRequest) atomicProcessModel.getTradeMessage();
            atomicProcessModel.updateFromCreateAtomicTxRequest(message);

            if (message.getTradePrice() != atomicProcessModel.getTradePrice())
                failed("Unexpected trade price");
//            atomicTrade. setTradePrice(message.getTradePrice());
            atomicProcessModel.getTradingPeer().setPubKeyRing(checkNotNull(message.getTakerPubKeyRing()));
            atomicTrade.setPeerNodeAddress(atomicProcessModel.getTempTradingPeerNodeAddress());
            atomicTrade.setAmount(Coin.valueOf(message.getBtcTradeAmount()));

            if (message.getBsqTradeAmount() < atomicProcessModel.getBsqMinTradeAmount() ||
                    message.getBsqTradeAmount() > atomicProcessModel.getBsqMaxTradeAmount())
                failed("bsqTradeAmount not within range");
            if (message.getBtcTradeAmount() < atomicProcessModel.getBtcMinTradeAmount() ||
                    message.getBtcTradeAmount() > atomicProcessModel.getBtcMaxTradeAmount())
                failed("btcTradeAmount not within range");
            if (message.getTakerFee() !=
                    (message.isCurrencyForTakerFeeBtc() ? atomicProcessModel.getBtcTradeFee() : atomicProcessModel.getBsqTradeFee()))
                failed("Taker fee mismatch");
//            var bsqAmount = atomicProcessModel.bsqAmountFromVolume(atomicTrade.getTradeVolume()).orElse(null);
            var bsqAmount = atomicProcessModel.bsqAmountFromVolume(atomicTrade.getOffer().getVolume()).orElse(null);
            if (bsqAmount == null || bsqAmount != message.getBsqTradeAmount())
                failed("Amounts don't match price");
            // TODO verify txFee is reasonable

            // Verify taker bsq address
            atomicProcessModel.getBsqWalletService().getBsqFormatter().getAddressFromBsqAddress(
                    message.getTakerBsqOutputAddress());

            var makerAddress = atomicProcessModel.getBtcWalletService().getOrCreateAddressEntry(
                    atomicTrade.getId(), AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            List<TransactionInput> makerBtcInputs = new ArrayList<>();
            if (!isBuyer) {
                makerBtcInputs = atomicProcessModel.getTradeWalletService().getInputsForAddress(makerAddress,
                        atomicTrade.getAmount());
            }
            // Inputs
            var makerBsqInputAmount = 0L;
            var makerBsqOutputAmount = message.getBsqTradeAmount();
            if (isBuyer) {
                // TODO Reserve BSQ for trade
                // Prepare BSQ as it's not part of the prepared tx
                var requiredBsq = atomicProcessModel.getBsqTradeAmount();
                var preparedBsq = atomicProcessModel.getBsqWalletService().prepareAtomicBsqInputs(Coin.valueOf(requiredBsq));
                makerBsqInputAmount = atomicProcessModel.getBsqWalletService().getConfirmedBsqInputAmount(
                        preparedBsq.first.getInputs(), atomicProcessModel.getDaoFacade());
                makerBsqOutputAmount = preparedBsq.second.getValue();
                atomicProcessModel.setMakerBsqInputs(preparedBsq.first.getInputs());
            }

            var takerBsqInputAmount = atomicProcessModel.getBsqWalletService().getBsqRawInputAmount(
                    message.getTakerBsqInputs(), atomicProcessModel.getDaoFacade());
            var takerBtcInputAmount = atomicProcessModel.getBtcWalletService().getBtcRawInputAmount(
                    message.getTakerBtcInputs());
            var makerBtcInputAmount = atomicProcessModel.getBtcWalletService().getBtcInputAmount(makerBtcInputs);

            // Outputs and fees
            var takerBsqOutputAmount = message.getTakerBsqOutputValue();
            var takerBtcOutputAmount = message.getTakerBtcOutputValue();
            var makerBtcOutputAmount = isBuyer ? message.getBtcTradeAmount() :
                    makerBtcInputAmount - message.getBtcTradeAmount();
            var btcTradeFeeAmount = atomicProcessModel.getBtcTradeFee();
            var bsqTradeFee = atomicProcessModel.getBsqTradeFee();
            var txFee = message.getTxFeePerVbyte();

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
            atomicProcessModel.setMakerBtcInputs(makerBtcInputs);
            atomicProcessModel.setMakerBtcOutputAmount(makerBtcOutputAmount);
            atomicProcessModel.setMakerBsqOutputAmount(makerBsqOutputAmount);
            atomicProcessModel.setTxFeePerVbyte(message.getTxFeePerVbyte());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
