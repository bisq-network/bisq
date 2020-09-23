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

package bisq.core.trade.protocol;

import bisq.core.offer.Offer;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.buyer.BuyerSendCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.tasks.buyer.BuyerSetupDepositTxListener;
import bisq.core.trade.protocol.tasks.buyer.BuyerSetupPayoutTxListener;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import static com.google.common.base.Preconditions.checkNotNull;

public interface BuyerProtocol {
    void onFiatPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler);

    enum BuyerEvent implements TradeProtocol.Event {
        STARTUP,
        PAYMENT_SENT
    }

    default void maybeSetupTaskRunners(Trade trade, ProcessModel processModel, TradeProtocol tradeProtocol) {
        Offer offer = checkNotNull(trade.getOffer());
        processModel.getTradingPeer().setPubKeyRing(offer.getPubKeyRing());


        tradeProtocol.given(tradeProtocol.phase(Trade.Phase.TAKER_FEE_PUBLISHED)
                .with(BuyerEvent.STARTUP))
                .setup(tradeProtocol.tasks(BuyerSetupDepositTxListener.class))
                .executeTasks();

        tradeProtocol.given(tradeProtocol.anyPhase(Trade.Phase.FIAT_SENT, Trade.Phase.FIAT_RECEIVED)
                .with(BuyerEvent.STARTUP))
                .setup(tradeProtocol.tasks(BuyerSetupPayoutTxListener.class))
                .executeTasks();

        tradeProtocol.given(tradeProtocol.anyPhase(Trade.Phase.FIAT_SENT, Trade.Phase.FIAT_RECEIVED)
                .anyState(Trade.State.BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG,
                        Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG)
                .with(BuyerEvent.STARTUP))
                .setup(tradeProtocol.tasks(BuyerSendCounterCurrencyTransferStartedMessage.class))
                .executeTasks();
    }
}
