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

package io.bisq.core.trade.protocol.tasks.taker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.PublishDepositTxRequest;
import io.bisq.core.trade.protocol.TradingPeer;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.core.util.Validator.checkTradeId;
import static io.bisq.core.util.Validator.nonEmptyStringOf;

@Slf4j
public class TakerProcessPublishDepositTxRequest extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerProcessPublishDepositTxRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            PublishDepositTxRequest publishDepositTxRequest = (PublishDepositTxRequest) processModel.getTradeMessage();
            checkTradeId(processModel.getOfferId(), publishDepositTxRequest);
            checkNotNull(publishDepositTxRequest);

            final TradingPeer tradingPeer = processModel.getTradingPeer();
            tradingPeer.setPaymentAccountPayload(checkNotNull(publishDepositTxRequest.getMakerPaymentAccountPayload()));
            tradingPeer.setAccountId(nonEmptyStringOf(publishDepositTxRequest.getMakerAccountId()));
            tradingPeer.setMultiSigPubKey(checkNotNull(publishDepositTxRequest.getMakerMultiSigPubKey()));
            tradingPeer.setContractAsJson(nonEmptyStringOf(publishDepositTxRequest.getMakerContractAsJson()));
            tradingPeer.setContractSignature(nonEmptyStringOf(publishDepositTxRequest.getMakerContractSignature()));
            tradingPeer.setPayoutAddressString(nonEmptyStringOf(publishDepositTxRequest.getMakerPayoutAddressString()));
            tradingPeer.setRawTransactionInputs(checkNotNull(publishDepositTxRequest.getMakerInputs()));
            final byte[] preparedDepositTx = publishDepositTxRequest.getPreparedDepositTx();
            processModel.setPreparedDepositTx(checkNotNull(preparedDepositTx));

            // Maker has to sign preparedDepositTx. He cannot manipulate the preparedDepositTx - so we avoid to have a
            // challenge protocol for passing the nonce we want to get signed.
            tradingPeer.setAccountAgeWitnessNonce(publishDepositTxRequest.getPreparedDepositTx());
            tradingPeer.setAccountAgeWitnessSignature(publishDepositTxRequest.getAccountAgeWitnessSignatureOfPreparedDepositTx());

            tradingPeer.setCurrentDate(publishDepositTxRequest.getCurrentDate());

            checkArgument(publishDepositTxRequest.getMakerInputs().size() > 0);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());
            trade.setState(Trade.State.TAKER_RECEIVED_PUBLISH_DEPOSIT_TX_REQUEST);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
