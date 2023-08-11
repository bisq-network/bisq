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
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TakerProtocol;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.messages.DelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.CheckIfDaoStateIsInSync;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.MaybeCreateSubAccount;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_taker.SellerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_taker.SellerAsTakerSignsDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.CreateTakerFeeTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerPublishFeeTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerSendInputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerVerifyAndSignContract;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerVerifyMakerFeePayment;
import bisq.core.trade.protocol.bisq_v5.messages.BuyersRedirectSellerSignatureRequest;
import bisq.core.trade.protocol.bisq_v5.messages.InputsForDepositTxResponse_v5;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateSignedClaimTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerCreatesWarningTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerFinalizesOwnRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerFinalizesOwnWarningTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerProcessBuyersRedirectSellerSignatureRequest;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSendStagedPayoutTxRequest;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSendsBuyersRedirectSellerSignatureResponse;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSignsOwnRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSignsOwnWarningTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSignsPeersRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSignsPeersWarningTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller_as_taker.SellerAsTakerProcessesInputsForDepositTxResponse_v5;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsTakerProtocol_v5 extends BaseSellerProtocol_v5 implements TakerProtocol {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerProtocol_v5(SellerAsTakerTrade trade) {
        super(trade);
        Offer offer = checkNotNull(trade.getOffer());
        processModel.getTradePeer().setPubKeyRing(offer.getPubKeyRing());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMailboxMessage(TradeMessage message, NodeAddress peerNodeAddress) {
        super.onMailboxMessage(message, peerNodeAddress);
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
                        CheckIfDaoStateIsInSync.class,
                        MaybeCreateSubAccount.class,
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

    private void handle(InputsForDepositTxResponse_v5 message, NodeAddress peer) {
        expect(phase(Trade.Phase.INIT)
                .with(message)
                .from(peer))
                .setup(tasks(
                        SellerAsTakerProcessesInputsForDepositTxResponse_v5.class,

                        ApplyFilter.class,
                        TakerVerifyAndSignContract.class,
                        TakerPublishFeeTx.class,
                        SellerAsTakerSignsDepositTx.class,

                        // We create our warn tx and our signature for the MS script.
                        SellerCreatesWarningTx.class,
                        SellerSignsOwnWarningTx.class,

                        // We can now create the signed claim tx from out warn tx
                        // CreateSignedClaimTx.class,

                        // We create our redirect tx using the buyers warn tx output and our signature for the MS script
                        CreateRedirectTx.class,
                        SellerSignsOwnRedirectTx.class,


                        // We sign the buyers warn tx
                        SellerSignsPeersWarningTx.class,

                        // We send buyer sig for their warn tx and our warn and redirect tx including our signatures
                        SellerSendStagedPayoutTxRequest.class)
                        .withTimeout(120))
                .executeTasks();
    }

    protected void handle(BuyersRedirectSellerSignatureRequest message, NodeAddress peer) {
        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
                .with(message)
                .from(peer))
                .setup(tasks(
                        // We received the buyer's signature for our warn and redirect tx as well
                        // as the buyer's redirect tx and signature
                        SellerProcessBuyersRedirectSellerSignatureRequest.class,

                        // We have now the buyers sig for our warn tx and finalize it
                        SellerFinalizesOwnWarningTx.class,

                        // We have now the buyers sig for our redirect tx and finalize it
                        SellerFinalizesOwnRedirectTx.class,

                        // We can now create the signed claim tx from out warn tx
                        CreateSignedClaimTx.class,

                        // We sign the buyers redirect tx
                        SellerSignsPeersRedirectTx.class,

                        // We have all transactions but missing still the buyer's witness for the deposit tx.

                        // We send the buyer the requested signature for their redirect tx
                        SellerSendsBuyersRedirectSellerSignatureResponse.class))
                .executeTasks();
    }


    @Override
    protected void handle(DelayedPayoutTxSignatureResponse message, NodeAddress peer) {
        super.handle(message, peer);
    }

    @Override
    protected void handle(ShareBuyerPaymentAccountMessage message, NodeAddress peer) {
        super.handle(message, peer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message when buyer has clicked payment started button
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void handle(CounterCurrencyTransferStartedMessage message, NodeAddress peer) {
        super.handle(message, peer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction
    ///////////////////////////////////////////////////////////////////////////////////////////

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

        if (message instanceof InputsForDepositTxResponse_v5) {
            handle((InputsForDepositTxResponse_v5) message, peer);
        } else if (message instanceof BuyersRedirectSellerSignatureRequest) {
            handle((BuyersRedirectSellerSignatureRequest) message, peer);
        }
    }

    @Override
    protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass() {
        return TakerVerifyMakerFeePayment.class;
    }
}
