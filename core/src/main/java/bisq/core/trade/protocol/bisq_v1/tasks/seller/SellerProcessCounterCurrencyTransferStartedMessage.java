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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.validation.PayoutTxValidation;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.TransactionValidation.checkBitcoinAddress;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerProcessCounterCurrencyTransferStartedMessage extends TradeTask {
    private static final int MAX_COUNTER_CURRENCY_DATA_LENGTH = 100;

    public SellerProcessCounterCurrencyTransferStartedMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            CounterCurrencyTransferStartedMessage message = (CounterCurrencyTransferStartedMessage) processModel.getTradeMessage();
            checkNotNull(message);

            TradingPeer tradingPeer = processModel.getTradePeer();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();

            String counterCurrencyTxId = message.getCounterCurrencyTxId();
            if (counterCurrencyTxId != null) {
                checkArgument(counterCurrencyTxId.length() < MAX_COUNTER_CURRENCY_DATA_LENGTH,
                        "counterCurrencyTxId must be shorter than %s chars", MAX_COUNTER_CURRENCY_DATA_LENGTH);
                trade.setCounterCurrencyTxId(counterCurrencyTxId);
            }

            String counterCurrencyExtraData = message.getCounterCurrencyExtraData();
            if (counterCurrencyExtraData != null) {
                checkArgument(counterCurrencyExtraData.length() < MAX_COUNTER_CURRENCY_DATA_LENGTH,
                        "counterCurrencyExtraData must be shorter than %s chars", MAX_COUNTER_CURRENCY_DATA_LENGTH);
                trade.setCounterCurrencyExtraData(counterCurrencyExtraData);
            }

            // Verify if buyers signature is valid for payout transaction
            String buyerPayoutAddress = checkBitcoinAddress(message.getBuyerPayoutAddress(), btcWalletService);
            byte[] buyerMultiSigPubKey = checkNotNull(tradingPeer.getMultiSigPubKey(),
                    "tradingPeer.getMultiSigPubKey() must not be null");
            byte[] sellerMultiSigPubKey = checkNotNull(processModel.getMyMultiSigPubKey(),
                    "processModel.getMyMultiSigPubKey() must not be null");
            byte[] buyerSignature = PayoutTxValidation.checkBuyersPayoutTxSignature(message.getBuyerSignature(),
                    buyerPayoutAddress,
                    trade,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey,
                    btcWalletService);

            tradingPeer.setPayoutAddressString(buyerPayoutAddress);
            tradingPeer.setSignature(buyerSignature);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            trade.setState(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
