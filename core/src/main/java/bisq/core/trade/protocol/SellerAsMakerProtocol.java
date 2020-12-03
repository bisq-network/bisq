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


import bisq.core.trade.SellerAsMakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.messages.DelayedPayoutTxSignatureResponse;
import bisq.core.trade.messages.DepositTxMessage;
import bisq.core.trade.messages.InputsForDepositTxRequest;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.ApplyFilter;
import bisq.core.trade.protocol.tasks.TradeTask;
import bisq.core.trade.protocol.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.tasks.maker.MakerProcessesInputsForDepositTxRequest;
import bisq.core.trade.protocol.tasks.maker.MakerRemovesOpenOffer;
import bisq.core.trade.protocol.tasks.maker.MakerSetsLockTime;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerFeePayment;
import bisq.core.trade.protocol.tasks.seller.SellerCreatesDelayedPayoutTx;
import bisq.core.trade.protocol.tasks.seller.SellerSendDelayedPayoutTxSignatureRequest;
import bisq.core.trade.protocol.tasks.seller.SellerSignsDelayedPayoutTx;
import bisq.core.trade.protocol.tasks.seller_as_maker.SellerAsMakerCreatesUnsignedDepositTx;
import bisq.core.trade.protocol.tasks.seller_as_maker.SellerAsMakerFinalizesDepositTx;
import bisq.core.trade.protocol.tasks.seller_as_maker.SellerAsMakerProcessDepositTxMessage;
import bisq.core.trade.protocol.tasks.seller_as_maker.SellerAsMakerSendsInputsForDepositTxResponse;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerAsMakerProtocol extends SellerProtocol implements MakerProtocol {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsMakerProtocol(SellerAsMakerTrade trade) {
        super(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle take offer request
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(InputsForDepositTxRequest message,
                                       NodeAddress peer,
                                       ErrorMessageHandler errorMessageHandler) {
        expect(phase(Trade.Phase.INIT)
                .with(message)
                .from(peer))
                .setup(tasks(
                        MakerProcessesInputsForDepositTxRequest.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class,
                        getVerifyPeersFeePaymentClass(),
                        MakerSetsLockTime.class,
                        MakerCreateAndSignContract.class,
                        SellerAsMakerCreatesUnsignedDepositTx.class,
                        SellerAsMakerSendsInputsForDepositTxResponse.class)
                        .using(new TradeTaskRunner(trade,
                                () -> handleTaskRunnerSuccess(message),
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(message, errorMessage);
                                }))
                        .withTimeout(60))
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming messages Take offer process
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(DepositTxMessage message, NodeAddress peer) {
        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
                .with(message)
                .from(peer))
                .setup(tasks(
                        MakerRemovesOpenOffer.class,
                        SellerAsMakerProcessDepositTxMessage.class,
                        SellerAsMakerFinalizesDepositTx.class,
                        SellerCreatesDelayedPayoutTx.class,
                        SellerSignsDelayedPayoutTx.class,
                        SellerSendDelayedPayoutTxSignatureRequest.class)
                        .withTimeout(60))
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

        if (message instanceof DepositTxMessage) {
            handle((DepositTxMessage) message, peer);
        }
    }

    @Override
    protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass() {
        return MakerVerifyTakerFeePayment.class;
    }
}
