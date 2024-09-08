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

import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.MakerProtocol;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bisq_v1.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.CheckIfDaoStateIsInSync;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerProcessesInputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerRemovesOpenOffer;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerSetsLockTime;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerVerifyTakerFeePayment;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.MaybeCreateSubAccount;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesTradeStatistics;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_maker.SellerAsMakerCreatesUnsignedDepositTx;
import bisq.core.trade.protocol.bisq_v5.messages.PreparedTxBuyerSignaturesMessage;
import bisq.core.trade.protocol.bisq_v5.tasks.AddWatchedScriptsToWallet;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateFeeBumpAddressEntries;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateRedirectTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.CreateWarningTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.FinalizeRedirectTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.FinalizeWarningTxs;
import bisq.core.trade.protocol.bisq_v5.tasks.maker.MakerSendsInputsForDepositTxResponse_v5;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerProcessPreparedTxBuyerSignaturesMessage;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSendsDepositTxAndSellerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSignsOwnRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSignsOwnWarningTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSignsPeersRedirectTx;
import bisq.core.trade.protocol.bisq_v5.tasks.seller.SellerSignsPeersWarningTx;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerAsMakerProtocol_v5 extends BaseSellerProtocol_v5 implements MakerProtocol {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsMakerProtocol_v5(SellerAsMakerTrade trade) {
        super(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMailboxMessage(TradeMessage message, NodeAddress peerNodeAddress) {
        super.onMailboxMessage(message, peerNodeAddress);
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
                        MaybeCreateSubAccount.class,
                        MakerProcessesInputsForDepositTxRequest.class,
                        ApplyFilter.class,
                        getVerifyPeersFeePaymentClass(),
                        MakerSetsLockTime.class,
                        MakerCreateAndSignContract.class,
                        SellerAsMakerCreatesUnsignedDepositTx.class,

                        CreateFeeBumpAddressEntries.class,
                        CreateWarningTxs.class,
                        CreateRedirectTxs.class,
                        SellerSignsOwnWarningTx.class,
                        SellerSignsPeersWarningTx.class,
                        SellerSignsOwnRedirectTx.class,
                        SellerSignsPeersRedirectTx.class,

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

//    protected void handle(DepositTxMessage message, NodeAddress peer) {
//        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
//                .with(message)
//                .from(peer))
//                .setup(tasks(
//                        MakerRemovesOpenOffer.class,
//                        SellerAsMakerProcessDepositTxMessage.class,
//                        SellerAsMakerFinalizesDepositTx.class,
//                        SellerCreatesDelayedPayoutTx.class,
//                        SellerSignsDelayedPayoutTx.class,
//                        SellerSendDelayedPayoutTxSignatureRequest.class)
//                        .withTimeout(120))
//                .executeTasks();
//    }

    @Override
    protected void handle(PreparedTxBuyerSignaturesMessage message, NodeAddress peer) {
        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
                .with(message)
                .from(peer))
                .setup(tasks(
                        SellerProcessPreparedTxBuyerSignaturesMessage.class,
                        FinalizeWarningTxs.class,
                        FinalizeRedirectTxs.class,
                        MakerRemovesOpenOffer.class,
                        AddWatchedScriptsToWallet.class,
                        SellerSendsDepositTxAndSellerPaymentAccountMessage.class,
                        SellerPublishesDepositTx.class,
                        SellerPublishesTradeStatistics.class))
                .executeTasks();
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
    }

    @Override
    protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass() {
        return MakerVerifyTakerFeePayment.class;
    }
}
