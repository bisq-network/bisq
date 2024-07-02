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

package bisq.core.trade.protocol.bisq_v5;

import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TakerProtocol;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.messages.PayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.CheckIfDaoStateIsInSync;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSetupDepositTxListener;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_taker.BuyerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_taker.BuyerAsTakerSignsDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.CreateTakerFeeTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerPublishFeeTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerSendInputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerVerifyAndSignContract;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerVerifyMakerFeePayment;
import bisq.core.trade.protocol.bisq_v5.messages.InputsForDepositTxResponse_v5;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateRedirectTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateWarningTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.FinalizeRedirectTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.FinalizeWarningTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSendsPreparedTxBuyerSignaturesMessage;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateFeeBumpAddressEntries;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSignsOwnRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSignsOwnWarningTx;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSignsPeersRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSignsPeersWarningTx;
import bisq.core.trade.protocol.bisq_v5.tasks.taker.TakerProcessInputsForDepositTxResponse_v5;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerAsTakerProtocol_v5 extends BaseBuyerProtocol_v5 implements TakerProtocol {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsTakerProtocol_v5(BuyerAsTakerTrade trade) {
        super(trade);

        Offer offer = checkNotNull(trade.getOffer());
        processModel.getTradePeer().setPubKeyRing(offer.getPubKeyRing());
    }

    @Override
    protected void onInitialized() {
        super.onInitialized();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMailboxMessage(TradeMessage message, NodeAddress peer) {
        super.onMailboxMessage(message, peer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Take offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onTakeOffer() {
        expect(phase(Trade.Phase.INIT)
                .with(TakerEvent.TAKE_OFFER))
                .setup(tasks(
                        CheckIfDaoStateIsInSync.class,
                        ApplyFilter.class,
                        getVerifyPeersFeePaymentClass(),
                        CreateTakerFeeTx.class,
                        BuyerAsTakerCreatesDepositTxInputs.class,
                        CreateFeeBumpAddressEntries.class,
                        TakerSendInputsForDepositTxRequest.class)
                        .withTimeout(120))
                .run(() -> {
                    processModel.setTempTradingPeerNodeAddress(trade.getTradingPeerNodeAddress());
                    processModel.getTradeManager().requestPersistence();
                })
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming messages Take offer process
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(InputsForDepositTxResponse_v5 message, NodeAddress peer) {
        expect(phase(Trade.Phase.INIT)
                .with(message)
                .from(peer))
                .setup(tasks(TakerProcessInputsForDepositTxResponse_v5.class,
                        ApplyFilter.class,

                        CreateWarningTxs.class,
                        CreateRedirectTxs.class,
                        BuyerSignsOwnWarningTx.class,
                        BuyerSignsPeersWarningTx.class,
                        BuyerSignsOwnRedirectTx.class,
                        BuyerSignsPeersRedirectTx.class,
                        FinalizeWarningTxs.class,
                        FinalizeRedirectTxs.class,

                        TakerVerifyAndSignContract.class,
                        TakerPublishFeeTx.class,
                        BuyerAsTakerSignsDepositTx.class,
                        BuyerSetupDepositTxListener.class,
                        BuyerSendsPreparedTxBuyerSignaturesMessage.class)
                        .withTimeout(120))
                .executeTasks();
    }

//    protected void handle(DelayedPayoutTxSignatureRequest message, NodeAddress peer) {
//        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
//                .with(message)
//                .from(peer))
//                .setup(tasks(
//                        BuyerProcessDelayedPayoutTxSignatureRequest.class,
//                        BuyerVerifiesPreparedDelayedPayoutTx.class,
//                        BuyerSignsDelayedPayoutTx.class,
//                        BuyerFinalizesDelayedPayoutTx.class,
//                        BuyerSendsDelayedPayoutTxSignatureResponse.class)
//                        .withTimeout(120))
//                .executeTasks();
//    }

    @Override
    protected void handle(DepositTxAndDelayedPayoutTxMessage message, NodeAddress peer) {
        super.handle(message, peer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        super.onPaymentStarted(resultHandler, errorMessageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message Payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void handle(PayoutTxPublishedMessage message, NodeAddress peer) {
        super.handle(message, peer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Message dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        super.onTradeMessage(message, peer);

        if (message instanceof InputsForDepositTxResponse_v5) {
            handle((InputsForDepositTxResponse_v5) message, peer);
        }
    }

    @Override
    protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass() {
        return TakerVerifyMakerFeePayment.class;
    }
}
