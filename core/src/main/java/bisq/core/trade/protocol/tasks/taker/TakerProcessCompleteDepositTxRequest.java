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
import bisq.core.trade.messages.CompleteDepositTxRequest;
import bisq.core.trade.protocol.TradingPeer;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkTradeId;
import static bisq.core.util.Validator.nonEmptyStringOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerProcessCompleteDepositTxRequest extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerProcessCompleteDepositTxRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            CompleteDepositTxRequest completeDepositTxRequest = (CompleteDepositTxRequest) processModel.getTradeMessage();
            checkTradeId(processModel.getOfferId(), completeDepositTxRequest);
            checkNotNull(completeDepositTxRequest);

            final TradingPeer tradingPeer = processModel.getTradingPeer();
            tradingPeer.setPaymentAccountPayload(checkNotNull(completeDepositTxRequest.getMakerPaymentAccountPayload()));
            tradingPeer.setAccountId(nonEmptyStringOf(completeDepositTxRequest.getMakerAccountId()));
            tradingPeer.setMultiSigPubKey(checkNotNull(completeDepositTxRequest.getMakerMultiSigPubKey()));
            tradingPeer.setContractAsJson(nonEmptyStringOf(completeDepositTxRequest.getMakerContractAsJson()));
            tradingPeer.setContractSignature(nonEmptyStringOf(completeDepositTxRequest.getMakerContractSignature()));
            tradingPeer.setPayoutAddressString(nonEmptyStringOf(completeDepositTxRequest.getMakerPayoutAddressString()));
            tradingPeer.setRawTransactionInputs(checkNotNull(completeDepositTxRequest.getMakerInputs()));
            final byte[] preparedDepositTx = completeDepositTxRequest.getPreparedDepositTx();
            processModel.setPreparedDepositTx(checkNotNull(preparedDepositTx));

            // Maker has to sign preparedDepositTx. He cannot manipulate the preparedDepositTx - so we avoid to have a
            // challenge protocol for passing the nonce we want to get signed.
            tradingPeer.setAccountAgeWitnessNonce(completeDepositTxRequest.getPreparedDepositTx());
            tradingPeer.setAccountAgeWitnessSignature(completeDepositTxRequest.getAccountAgeWitnessSignatureOfPreparedDepositTx());

            tradingPeer.setCurrentDate(completeDepositTxRequest.getCurrentDate());

            checkArgument(completeDepositTxRequest.getMakerInputs().size() > 0);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());
            trade.setState(Trade.State.TAKER_RECEIVED_PUBLISH_DEPOSIT_TX_REQUEST);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
