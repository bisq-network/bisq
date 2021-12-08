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

package bisq.core.trade.protocol.bisq_v1;


import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.messages.DelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerCreatesDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSendDelayedPayoutTxSignatureRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSignsDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_taker.SellerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_taker.SellerAsTakerSignsDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.CreateTakerFeeTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerProcessesInputsForDepositTxResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerPublishFeeTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerSendInputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerVerifyAndSignContract;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerVerifyMakerFeePayment;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsTakerProtocol extends SellerProtocol implements TakerProtocol {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerProtocol(SellerAsTakerTrade trade) {
        super(trade);
        Offer offer = checkNotNull(trade.getOffer());
        processModel.getTradePeer().setPubKeyRing(offer.getPubKeyRing());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction: Take offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onTakeOffer() {
        expect(phase(Trade.Phase.INIT)
                .with(TakerEvent.TAKE_OFFER)
                .from(trade.getTradingPeerNodeAddress()))
                .setup(tasks(
                        ApplyFilter.class,
                        getVerifyPeersFeePaymentClass(),
                        CreateTakerFeeTx.class,
                        SellerAsTakerCreatesDepositTxInputs.class,
                        TakerSendInputsForDepositTxRequest.class)
                        .withTimeout(120))
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming messages Take offer process
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(InputsForDepositTxResponse message, NodeAddress peer) {
        expect(phase(Trade.Phase.INIT)
                .with(message)
                .from(peer))
                .setup(tasks(
                        TakerProcessesInputsForDepositTxResponse.class,
                        ApplyFilter.class,
                        TakerVerifyAndSignContract.class,
                        TakerPublishFeeTx.class,
                        SellerAsTakerSignsDepositTx.class,
                        SellerCreatesDelayedPayoutTx.class,
                        SellerSignsDelayedPayoutTx.class,
                        SellerSendDelayedPayoutTxSignatureRequest.class)
                        .withTimeout(120))
                .executeTasks();
    }

    // We keep the handler here in as well to make it more transparent which messages we expect
    @Override
    protected void handle(DelayedPayoutTxSignatureResponse message, NodeAddress peer) {
        super.handle(message, peer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message when buyer has clicked payment started button
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We keep the handler here in as well to make it more transparent which messages we expect
    @Override
    protected void handle(CounterCurrencyTransferStartedMessage message, NodeAddress peer) {
        super.handle(message, peer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We keep the handler here in as well to make it more transparent which events we expect
    @Override
    public void onPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        super.onPaymentReceived(resultHandler, errorMessageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        super.onTradeMessage(message, peer);

        log.info("Received {} from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peer, message.getTradeId(), message.getUid());

        if (message instanceof InputsForDepositTxResponse) {
            handle((InputsForDepositTxResponse) message, peer);
        }
    }

    @Override
    protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass() {
        return TakerVerifyMakerFeePayment.class;
    }
}
