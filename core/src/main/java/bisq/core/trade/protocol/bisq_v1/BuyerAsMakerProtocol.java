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

import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bisq_v1.messages.DelayedPayoutTxSignatureRequest;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.messages.PayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerFinalizesDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerProcessDelayedPayoutTxSignatureRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSendsDelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSetupDepositTxListener;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSignsDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerVerifiesPreparedDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_maker.BuyerAsMakerSendsInputsForDepositTxResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerProcessesInputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerRemovesOpenOffer;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerSetsLockTime;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerVerifyTakerFeePayment;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerAsMakerProtocol extends BuyerProtocol implements MakerProtocol {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsMakerProtocol(BuyerAsMakerTrade trade) {
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
                        getVerifyPeersFeePaymentClass(),
                        MakerSetsLockTime.class,
                        MakerCreateAndSignContract.class,
                        BuyerAsMakerCreatesAndSignsDepositTx.class,
                        BuyerSetupDepositTxListener.class,
                        BuyerAsMakerSendsInputsForDepositTxResponse.class).
                        using(new TradeTaskRunner(trade,
                                () -> handleTaskRunnerSuccess(message),
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(message, errorMessage);
                                }))
                        .withTimeout(120))
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming messages Take offer process
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(DelayedPayoutTxSignatureRequest message, NodeAddress peer) {
        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
                .with(message)
                .from(peer))
                .setup(tasks(
                        MakerRemovesOpenOffer.class,
                        BuyerProcessDelayedPayoutTxSignatureRequest.class,
                        BuyerVerifiesPreparedDelayedPayoutTx.class,
                        BuyerSignsDelayedPayoutTx.class,
                        BuyerFinalizesDelayedPayoutTx.class,
                        BuyerSendsDelayedPayoutTxSignatureResponse.class)
                        .withTimeout(120))
                .executeTasks();
    }

    // We keep the handler here in as well to make it more transparent which messages we expect
    @Override
    protected void handle(DepositTxAndDelayedPayoutTxMessage message, NodeAddress peer) {
        super.handle(message, peer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We keep the handler here in as well to make it more transparent which events we expect
    @Override
    public void onPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        super.onPaymentStarted(resultHandler, errorMessageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message Payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We keep the handler here in as well to make it more transparent which messages we expect
    @Override
    protected void handle(PayoutTxPublishedMessage message, NodeAddress peer) {
        super.handle(message, peer);
    }


    @Override
    protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass() {
        return MakerVerifyTakerFeePayment.class;
    }
}
