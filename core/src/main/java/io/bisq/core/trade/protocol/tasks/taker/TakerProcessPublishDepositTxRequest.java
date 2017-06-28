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

            processModel.getTradingPeer().setPaymentAccountPayload(checkNotNull(publishDepositTxRequest.getMakerPaymentAccountPayload()));
            processModel.getTradingPeer().setAccountId(nonEmptyStringOf(publishDepositTxRequest.getMakerAccountId()));
            processModel.getTradingPeer().setMultiSigPubKey(checkNotNull(publishDepositTxRequest.getMakerMultiSigPubKey()));
            processModel.getTradingPeer().setContractAsJson(nonEmptyStringOf(publishDepositTxRequest.getMakerContractAsJson()));
            processModel.getTradingPeer().setContractSignature(nonEmptyStringOf(publishDepositTxRequest.getMakerContractSignature()));
            processModel.getTradingPeer().setPayoutAddressString(nonEmptyStringOf(publishDepositTxRequest.getMakerPayoutAddressString()));
            processModel.getTradingPeer().setRawTransactionInputs(checkNotNull(publishDepositTxRequest.getMakerInputs()));
            processModel.setPreparedDepositTx(checkNotNull(publishDepositTxRequest.getPreparedDepositTx()));
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