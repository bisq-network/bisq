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

import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.MakerProtocol;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.messages.PayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.CheckIfDaoStateIsInSync;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSetupDepositTxListener;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerProcessesInputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerRemovesOpenOffer;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerSetsLockTime;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerVerifyTakerFeePayment;
import bisq.core.trade.protocol.bisq_v5.messages.DepositTxAndSellerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v5.messages.PreparedTxBuyerSignaturesRequest;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateFeeBumpAddressEntries;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateRedirectTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateWarningTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.FinalizeRedirectTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.FinalizeWarningTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSendsPreparedTxBuyerSignaturesMessage;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSignsOwnRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSignsOwnWarningTx;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSignsPeersRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer.BuyerSignsPeersWarningTx;
import bisq.core.trade.protocol.bisq_v5.tasks.buyer_as_maker.BuyerAsMakerProcessPreparedTxBuyerSignaturesRequest;
import bisq.core.trade.protocol.bisq_v5.tasks.maker.MakerSendsInputsForDepositTxResponse_v5;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BuyerAsMakerProtocol_v5 extends BaseBuyerProtocol_v5 implements MakerProtocol {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsMakerProtocol_v5(BuyerAsMakerTrade trade) {
        super(trade);
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
                        CheckIfDaoStateIsInSync.class,
                        MakerProcessesInputsForDepositTxRequest.class,
                        ApplyFilter.class,
                        getVerifyPeersFeePaymentClass(),
                        MakerSetsLockTime.class,
                        MakerCreateAndSignContract.class,
                        BuyerAsMakerCreatesAndSignsDepositTx.class,
                        BuyerSetupDepositTxListener.class,

//                        // We create our warn tx and our signature for the MS script
                        CreateFeeBumpAddressEntries.class,
                        CreateWarningTxs.class,
                        CreateRedirectTxs.class,
                        BuyerSignsOwnWarningTx.class,
                        BuyerSignsPeersWarningTx.class,
                        BuyerSignsOwnRedirectTx.class,
                        BuyerSignsPeersRedirectTx.class,

                        MakerSendsInputsForDepositTxResponse_v5.class)

                        .using(new TradeTaskRunner(trade,
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

    protected void handle(PreparedTxBuyerSignaturesRequest message, NodeAddress peer) {
        checkArgument(Version.isTradeProtocolVersion5Activated());
        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
                .with(message)
                .from(peer))
                .setup(tasks(
                        BuyerAsMakerProcessPreparedTxBuyerSignaturesRequest.class,
                        FinalizeWarningTxs.class,
                        FinalizeRedirectTxs.class,
                        MakerRemovesOpenOffer.class,
                        BuyerSendsPreparedTxBuyerSignaturesMessage.class)
                        .withTimeout(120))
                .executeTasks();
    }

//    protected void handle(StagedPayoutTxRequest message, NodeAddress peer) {
//        checkArgument(Version.isTradeProtocolVersion5Activated());
//        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
//                .with(message)
//                .from(peer))
//                .setup(tasks(
//                        // We received the sellers sig for our warn tx and the sellers warn and redirect tx as well the sellers signatures
//                        BuyerProcessStagedPayoutTxRequest.class,
//                        BuyerVerifiesWarningAndRedirectTxs.class,
//
//                        MakerRemovesOpenOffer.class,
//
//                        // We sign sellers warn and redirect tx
//                        BuyerSignsPeersWarningTx.class,
//                        BuyerSignsPeersRedirectTx.class,
//
//                        // We have now all for finalizing our warn tx
//                        BuyerFinalizesOwnWarningTx.class,
//
//                        // We create and sign our redirect tx with the input from the sellers warn tx
//                        CreateRedirectTx.class,
//                        BuyerSignsOwnRedirectTx.class,
//
//                        // As we have our finalized warn tx, we can create the signed claim tx
//                        CreateSignedClaimTx.class,
//
//                        // We have now:
//                        // - our finalized warn tx
//                        // - our signed claim tx
//                        // - our redirect tx + our sig
//                        //
//                        // Missing:
//                        // - sellers sig for our redirect tx
//                        // - sellers sig for the deposit tx
//
//                        // We do not send yet the signed deposit tx as we require first to have all txs completed.
//                        // We request from the seller the signature for the redirect tx
//                        // We send seller the signatures for the sellers warn and redirect tx,
//                        // as well as our redirect tx and its signature
//                        BuyerSendsBuyersRedirectSellerSignatureRequest.class)
//                        .withTimeout(120))
//                .executeTasks();
//    }
//
//    protected void handle(DelayedPayoutTxSignatureRequest message, NodeAddress peer) {
//        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
//                .with(message)
//                .from(peer))
//                .setup(tasks(
//                        MakerRemovesOpenOffer.class,
//                        BuyerProcessDelayedPayoutTxSignatureRequest.class,
//                        BuyerVerifiesPreparedDelayedPayoutTx.class,
//                        BuyerSignsDelayedPayoutTx.class,
//                        BuyerFinalizesDelayedPayoutTx.class,
//                        BuyerSendsDelayedPayoutTxSignatureResponse.class)
//                        .withTimeout(120))
//                .executeTasks();
//    }

    @Override
    protected void handle(DepositTxAndSellerPaymentAccountMessage message, NodeAddress peer) {
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

        if (message instanceof PreparedTxBuyerSignaturesRequest) {
            handle((PreparedTxBuyerSignaturesRequest) message, peer);
        }
    }


    @Override
    protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass() {
        return MakerVerifyTakerFeePayment.class;
    }
}
