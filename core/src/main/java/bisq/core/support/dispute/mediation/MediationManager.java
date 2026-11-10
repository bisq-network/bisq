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

package bisq.core.support.dispute.mediation;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.agent.DisputeAgentLookupMap;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.dispute.messages.OpenNewDisputeMessage;
import bisq.core.support.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.DisputeProtocol;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.security.PublicKey;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Singleton
public final class MediationManager extends DisputeManager<MediationDisputeList> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MediationManager(P2PService p2PService,
                            TradeWalletService tradeWalletService,
                            BtcWalletService walletService,
                            WalletsSetup walletsSetup,
                            TradeManager tradeManager,
                            ClosedTradableManager closedTradableManager,
                            FailedTradesManager failedTradesManager,
                            OpenOfferManager openOfferManager,
                            DaoFacade daoFacade,
                            KeyRing keyRing,
                            MediationDisputeListService mediationDisputeListService,
                            Config config,
                            PriceFeedService priceFeedService) {
        super(p2PService, tradeWalletService, walletService, walletsSetup, tradeManager, closedTradableManager, failedTradesManager,
                openOfferManager, daoFacade, keyRing, mediationDisputeListService, config, priceFeedService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement template methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SupportType getSupportType() {
        return SupportType.MEDIATION;
    }

    @Override
    public void onSupportMessage(SupportMessage message, PublicKey senderSignaturePubKey) {
        if (canProcessMessage(message)) {
            log.info("Received {} with tradeId {} and uid {}",
                    message.getClass().getSimpleName(), message.getTradeId(), message.getUid());

            if (message instanceof OpenNewDisputeMessage openNewDisputeMessage) {
                onOpenNewDisputeMessage(openNewDisputeMessage, senderSignaturePubKey);
            } else if (message instanceof PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
                onPeerOpenedDisputeMessage(peerOpenedDisputeMessage, senderSignaturePubKey);
            } else if (message instanceof ChatMessage chatMessage) {
                onChatMessage(chatMessage, senderSignaturePubKey);
            } else if (message instanceof DisputeResultMessage disputeResultMessage) {
                onDisputeResultMessage(disputeResultMessage, senderSignaturePubKey);
            } else {
                log.warn("Unsupported message at dispatchMessage. message={}", message);
            }
        }
    }

    @Override
    protected Trade.DisputeState getDisputeStateStartedByPeer() {
        return Trade.DisputeState.MEDIATION_STARTED_BY_PEER;
    }

    // A new mediation would take a trade whose mediation has a result back to MEDIATION_STARTED_BY_PEER, where the
    // result is neither accepted nor published, and a trade in arbitration back to mediation
    @Override
    protected boolean isDisputeStartedByPeerAllowed(Trade.DisputeState disputeState) {
        return disputeState == Trade.DisputeState.NO_DISPUTE ||
                disputeState == Trade.DisputeState.MEDIATION_REQUESTED ||
                disputeState == Trade.DisputeState.MEDIATION_STARTED_BY_PEER;
    }

    @Override
    protected AckMessageSourceType getAckMessageSourceType() {
        return AckMessageSourceType.MEDIATION_MESSAGE;
    }

    @Override
    public void cleanupDisputes() {
        // closes any trades/disputes which paid out while Bisq was not in use
        disputeListService.cleanupDisputes(tradeId -> tradeManager.getTradeById(tradeId).filter(trade -> trade.getPayoutTx() != null)
                .ifPresent(trade -> {
                    tradeManager.closeDisputedTrade(tradeId, Trade.DisputeState.MEDIATION_CLOSED);
                    findOwnDispute(tradeId).ifPresent(Dispute::setIsClosed);
                }));
    }

    @Override
    protected String getDisputeInfo(Dispute dispute) {
        String role = Res.get("shared.mediator").toLowerCase();
        NodeAddress agentNodeAddress = getAgentNodeAddress(dispute);
        checkNotNull(agentNodeAddress, "Agent node address must not be null");
        String roleContextMsg = Res.get("support.initialMediatorMsg",
                DisputeAgentLookupMap.getMatrixLinkForAgent(agentNodeAddress.getFullAddress()));
        String link = "https://bisq.wiki/Dispute_resolution#Level_2:_Mediation";
        return Res.get("support.initialInfo", role, roleContextMsg, role, link);
    }

    @Override
    protected String getDisputeIntroForPeer(String disputeInfo) {
        return Res.get("support.peerOpenedDisputeForMediation", disputeInfo, Version.VERSION);
    }

    @Override
    protected String getDisputeIntroForDisputeCreator(String disputeInfo) {
        return Res.get("support.youOpenedDisputeForMediation", disputeInfo, Version.VERSION);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    // We get that message at both peers. The dispute object is in context of the trader
    public void onDisputeResultMessage(DisputeResultMessage disputeResultMessage, PublicKey senderSignaturePubKey) {
        DisputeResult disputeResult = disputeResultMessage.getDisputeResult();
        String tradeId = disputeResult.getTradeId();
        ChatMessage chatMessage = disputeResult.getChatMessage();
        checkNotNull(chatMessage, "chatMessage must not be null");
        Optional<Dispute> disputeOptional = findDispute(disputeResult);
        String uid = disputeResultMessage.getUid();
        if (disputeOptional.isEmpty()) {
            log.warn("We got a dispute result msg but we don't have a matching dispute. " +
                    "That might happen when we get the disputeResultMessage before the dispute was created. " +
                    "We try again after 2 sec. to apply the disputeResultMessage. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                // We delay 2 sec. to be sure the comm. msg gets added first
                Timer timer = UserThread.runAfter(() -> onDisputeResultMessage(disputeResultMessage, senderSignaturePubKey), 2);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a dispute result msg after we already repeated to apply the message after a delay. " +
                        "That should never happen. TradeId = " + tradeId);
            }
            return;
        }

        Dispute dispute = disputeOptional.get();
        if (!isDisputeAgentSignaturePubKeyValid(dispute,
                senderSignaturePubKey,
                disputeResultMessage.getClass().getSimpleName())) {
            return;
        }

        cleanupRetryMap(uid);
        // The mediator cannot change the payout once a result was sent, so a result with another payout is no
        // correction. We keep the result we have, and the mediator gets the refusal in the ACK.
        if (hasResultWithOtherPayout(tradeId, disputeResult)) {
            log.warn("We refuse a mediation result whose payout differs from the result we have for the trade. " +
                    "tradeId={}, uid={}", tradeId, uid);
            addOtherPayoutRefusedMessage(dispute);
            sendAckMessage(chatMessage, dispute.getAgentPubKeyRing(), false,
                    "The payout differs from the mediation result that the trader has for this trade");
            requestPersistence();
            return;
        }

        if (!dispute.getChatMessages().contains(chatMessage)) {
            dispute.addAndPersistChatMessage(chatMessage);
        } else {
            log.warn("We got a dispute mail msg what we have already stored. TradeId = " + chatMessage.getTradeId());
        }
        dispute.setState(Dispute.State.RESULT_PROPOSED);

        dispute.setDisputeResult(disputeResult);

        Optional<Trade> tradeOptional = tradeManager.getTradeById(tradeId);
        if (tradeOptional.isPresent()) {
            Trade trade = tradeOptional.get();
            if (trade.getDisputeState() == Trade.DisputeState.MEDIATION_REQUESTED ||
                    trade.getDisputeState() == Trade.DisputeState.MEDIATION_STARTED_BY_PEER) {
                trade.getProcessModel().setBuyerPayoutAmountFromMediation(disputeResult.getBuyerPayoutAmount().value);
                trade.getProcessModel().setSellerPayoutAmountFromMediation(disputeResult.getSellerPayoutAmount().value);

                trade.setDisputeState(Trade.DisputeState.MEDIATION_CLOSED);

                tradeManager.requestPersistence();
                checkForMediatedTradePayout(trade, dispute);
            }
        } else {
            Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(tradeId);
            openOfferOptional.ifPresent(openOffer -> openOfferManager.closeOpenOffer(openOffer.getOffer()));
        }
        sendAckMessage(chatMessage, dispute.getAgentPubKeyRing(), true, null);

        maybeClearSensitiveData();
        requestPersistence();
    }


    // A trader's node keeps the payout of the first mediation result that it received for a trade
    private boolean hasResultWithOtherPayout(String tradeId, DisputeResult disputeResult) {
        return getDisputeList().stream()
                .filter(dispute -> dispute.getTradeId().equals(tradeId))
                .map(dispute -> dispute.getDisputeResultProperty().get())
                .filter(Objects::nonNull)
                .anyMatch(result -> !result.hasSamePayout(disputeResult));
    }

    private void addOtherPayoutRefusedMessage(Dispute dispute) {
        ChatMessage chatMessage = new ChatMessage(getSupportType(),
                dispute.getTradeId(),
                pubKeyRing.hashCode(),
                false,
                Res.get("support.systemMsg", Res.get("support.mediationResult.otherPayoutRefused")),
                p2PService.getAddress());
        chatMessage.setSystemMessage(true);
        dispute.addAndPersistChatMessage(chatMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    @Override
    public NodeAddress getAgentNodeAddress(Dispute dispute) {
        return dispute.getContract().getMediatorNodeAddress();
    }

    // The payout of a trade is final once the mediator has sent a result for the trade to one trader. The traders
    // sign the payout of the result they received, so a changed payout could only be a second, conflicting result.
    @Override
    public void sendDisputeResultMessage(DisputeResult disputeResult, Dispute dispute, String summaryText) {
        checkArgument(hasPayoutOfSentResults(dispute, disputeResult),
                "The payout differs from a mediation result that was sent for trade %s", dispute.getTradeId());
        super.sendDisputeResultMessage(disputeResult, dispute, summaryText);
    }

    /**
     * Returns the result that fixes the payout of a mediator's ticket: the result sent for this ticket, or else the
     * first result sent for the other ticket of the trade. Empty while no result was sent for the trade.
     */
    public Optional<DisputeResult> findSentResult(Dispute dispute) {
        return getSentResult(dispute).or(() -> getDisputeList().stream()
                .filter(other -> other.getTradeId().equals(dispute.getTradeId()) &&
                        other.getTraderId() != dispute.getTraderId())
                .map(MediationManager::getSentResult)
                .flatMap(Optional::stream)
                .min(Comparator.comparing(DisputeResult::getCloseDate)));
    }

    // Whether the payout of the result is the payout of every result that was sent for the trade of the dispute
    public boolean hasPayoutOfSentResults(Dispute dispute, DisputeResult disputeResult) {
        return getDisputeList().stream()
                .filter(other -> other.getTradeId().equals(dispute.getTradeId()))
                .map(MediationManager::getSentResult)
                .flatMap(Optional::stream)
                .allMatch(sentResult -> sentResult.hasSamePayout(disputeResult));
    }

    private static Optional<DisputeResult> getSentResult(Dispute dispute) {
        return Optional.ofNullable(dispute.getDisputeResultProperty().get())
                .filter(DisputeResult::isSent);
    }

    @Nullable
    @Override
    protected PubKeyRing getExpectedAgentPubKeyRing(Trade trade) {
        return trade.getMediatorPubKeyRing();
    }

    public void onAcceptMediationResult(Trade trade,
                                        ResultHandler resultHandler,
                                        ErrorMessageHandler errorMessageHandler) {
        String tradeId = trade.getId();
        Optional<Dispute> optionalDispute = findDispute(tradeId);
        checkArgument(optionalDispute.isPresent(), "dispute must be present");
        DisputeResult disputeResult = optionalDispute.get().getDisputeResultProperty().get();
        ProcessModel processModel = trade.getProcessModel();
        // Our signature is the acceptance, and we sign only once (SignMediatedPayoutTx). The amounts that it covers
        // must not change afterwards, so only the first acceptance takes the amounts of the result.
        if (processModel.getMediatedPayoutTxSignature() == null) {
            applyAmountsOfAcceptedResult(tradeId, processModel, disputeResult);
        }
        DisputeProtocol tradeProtocol = (DisputeProtocol) tradeManager.getTradeProtocol(trade);

        trade.setMediationResultState(MediationResultState.MEDIATION_RESULT_ACCEPTED);
        tradeManager.requestPersistence();

        // If we have not got yet the peers signature we sign and send to the peer our signature.
        // Otherwise we sign and complete with the peers signature the payout tx.
        if (processModel.getTradePeer().getMediatedPayoutTxSignature() == null) {
            tradeProtocol.onAcceptMediationResult(() -> {
                if (trade.getPayoutTx() != null) {
                    tradeManager.closeDisputedTrade(tradeId, Trade.DisputeState.MEDIATION_CLOSED);
                }
                resultHandler.handleResult();
            }, errorMessageHandler);
        } else {
            tradeProtocol.onFinalizeMediationResultPayout(() -> {
                if (trade.getPayoutTx() != null) {
                    tradeManager.closeDisputedTrade(tradeId, Trade.DisputeState.MEDIATION_CLOSED);
                }
                resultHandler.handleResult();
            }, errorMessageHandler);
        }
    }

    // The peer's stored signature was checked against the amounts that the trade had then. A result that replaced
    // another one before this version can have other amounts, which the peer's signature does not cover.
    private static void applyAmountsOfAcceptedResult(String tradeId,
                                                     ProcessModel processModel,
                                                     DisputeResult disputeResult) {
        long buyerPayoutAmount = disputeResult.getBuyerPayoutAmount().value;
        long sellerPayoutAmount = disputeResult.getSellerPayoutAmount().value;
        TradingPeer tradingPeer = processModel.getTradePeer();
        if (tradingPeer.getMediatedPayoutTxSignature() != null &&
                (buyerPayoutAmount != processModel.getBuyerPayoutAmountFromMediation() ||
                        sellerPayoutAmount != processModel.getSellerPayoutAmountFromMediation())) {
            log.warn("We drop the peer's signature of the mediated payout, as it covers other amounts than the " +
                    "result we accept. tradeId={}", tradeId);
            tradingPeer.setMediatedPayoutTxSignature(null);
        }
        processModel.setBuyerPayoutAmountFromMediation(buyerPayoutAmount);
        processModel.setSellerPayoutAmountFromMediation(sellerPayoutAmount);
    }

    public void rejectMediationResult(Trade trade) {
        trade.setMediationResultState(MediationResultState.MEDIATION_RESULT_REJECTED);
        tradeManager.requestPersistence();
    }

}
