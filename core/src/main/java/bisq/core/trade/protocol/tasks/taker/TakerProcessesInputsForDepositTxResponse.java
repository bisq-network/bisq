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

package bisq.core.trade.protocol.tasks.taker;

import bisq.core.trade.Trade;
import bisq.core.trade.messages.InputsForDepositTxResponse;
import bisq.core.trade.protocol.TradingPeer;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkTradeId;
import static bisq.core.util.Validator.nonEmptyStringOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerProcessesInputsForDepositTxResponse extends TradeTask {
    @SuppressWarnings({"unused"})
    public TakerProcessesInputsForDepositTxResponse(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            InputsForDepositTxResponse inputsForDepositTxResponse = (InputsForDepositTxResponse) processModel.getTradeMessage();
            checkTradeId(processModel.getOfferId(), inputsForDepositTxResponse);
            checkNotNull(inputsForDepositTxResponse);

            TradingPeer tradingPeer = processModel.getTradingPeer();
            tradingPeer.setPaymentAccountPayload(checkNotNull(inputsForDepositTxResponse.getMakerPaymentAccountPayload()));
            tradingPeer.setAccountId(nonEmptyStringOf(inputsForDepositTxResponse.getMakerAccountId()));
            tradingPeer.setMultiSigPubKey(checkNotNull(inputsForDepositTxResponse.getMakerMultiSigPubKey()));
            tradingPeer.setContractAsJson(nonEmptyStringOf(inputsForDepositTxResponse.getMakerContractAsJson()));
            tradingPeer.setContractSignature(nonEmptyStringOf(inputsForDepositTxResponse.getMakerContractSignature()));
            tradingPeer.setPayoutAddressString(nonEmptyStringOf(inputsForDepositTxResponse.getMakerPayoutAddressString()));
            tradingPeer.setRawTransactionInputs(checkNotNull(inputsForDepositTxResponse.getMakerInputs()));
            byte[] preparedDepositTx = inputsForDepositTxResponse.getPreparedDepositTx();
            processModel.setPreparedDepositTx(checkNotNull(preparedDepositTx));
            long lockTime = inputsForDepositTxResponse.getLockTime();
            //todo for dev testing deactivated
            //checkArgument(lockTime >= processModel.getBtcWalletService().getBestChainHeight() + 144 * 20);
            trade.setLockTime(lockTime);
            log.info("lockTime={}, delay={}", lockTime, (processModel.getBtcWalletService().getBestChainHeight() - lockTime));

            // Maker has to sign preparedDepositTx. He cannot manipulate the preparedDepositTx - so we avoid to have a
            // challenge protocol for passing the nonce we want to get signed.
            tradingPeer.setAccountAgeWitnessNonce(inputsForDepositTxResponse.getPreparedDepositTx());
            tradingPeer.setAccountAgeWitnessSignature(inputsForDepositTxResponse.getAccountAgeWitnessSignatureOfPreparedDepositTx());

            tradingPeer.setCurrentDate(inputsForDepositTxResponse.getCurrentDate());

            checkArgument(inputsForDepositTxResponse.getMakerInputs().size() > 0);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());
            trade.setState(Trade.State.TAKER_RECEIVED_PUBLISH_DEPOSIT_TX_REQUEST);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
