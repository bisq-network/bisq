/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.tasks.taker;

import io.bitsquare.common.crypto.CryptoUtil;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.payment.BlockChainAccountContractData;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.PublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.checkTradeId;
import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class ProcessPublishDepositTxRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessPublishDepositTxRequest.class);

    public ProcessPublishDepositTxRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            PublishDepositTxRequest publishDepositTxRequest = (PublishDepositTxRequest) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), publishDepositTxRequest);
            checkNotNull(publishDepositTxRequest);

            PaymentAccountContractData paymentAccountContractData = checkNotNull(publishDepositTxRequest.offererPaymentAccountContractData);
            processModel.tradingPeer.setPaymentAccountContractData(paymentAccountContractData);
            // We apply the payment ID in case its a cryptoNote coin. It is created form the hash of the trade ID
            if (paymentAccountContractData instanceof BlockChainAccountContractData &&
                    CurrencyUtil.isCryptoNoteCoin(processModel.getOffer().getCurrencyCode())) {
                String paymentId = CryptoUtil.getHashAsHex(trade.getId()).substring(0, 32);
                ((BlockChainAccountContractData) paymentAccountContractData).setPaymentId(paymentId);
            }

            processModel.tradingPeer.setAccountId(nonEmptyStringOf(publishDepositTxRequest.offererAccountId));
            processModel.tradingPeer.setTradeWalletPubKey(checkNotNull(publishDepositTxRequest.offererTradeWalletPubKey));
            processModel.tradingPeer.setContractAsJson(nonEmptyStringOf(publishDepositTxRequest.offererContractAsJson));
            processModel.tradingPeer.setContractSignature(nonEmptyStringOf(publishDepositTxRequest.offererContractSignature));
            processModel.tradingPeer.setPayoutAddressString(nonEmptyStringOf(publishDepositTxRequest.offererPayoutAddressString));
            processModel.tradingPeer.setRawInputs(checkNotNull(publishDepositTxRequest.offererInputs));
            processModel.setPreparedDepositTx(checkNotNull(publishDepositTxRequest.preparedDepositTx));
            checkArgument(publishDepositTxRequest.offererInputs.size() > 0);
            if (publishDepositTxRequest.openDisputeTimeAsBlockHeight != 0) {
                trade.setOpenDisputeTimeAsBlockHeight(publishDepositTxRequest.openDisputeTimeAsBlockHeight);
            } else {
                failed("waitPeriodForOpenDisputeAsBlockHeight = 0");
            }

            if (publishDepositTxRequest.checkPaymentTimeAsBlockHeight != 0) {
                trade.setCheckPaymentTimeAsBlockHeight(publishDepositTxRequest.checkPaymentTimeAsBlockHeight);
            } else {
                failed("notificationTimeAsBlockHeight = 0");
            }

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerAddress(processModel.getTempTradingPeerAddress());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}