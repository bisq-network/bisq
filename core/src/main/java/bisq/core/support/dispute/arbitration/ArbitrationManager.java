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

package bisq.core.support.dispute.arbitration;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.security.PublicKey;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Deprecated
@Slf4j
@Singleton
// TODO: Remove this compatibility manager after historical arbitration cases have a dedicated read-only service.
public final class ArbitrationManager extends DisputeManager<ArbitrationDisputeList> {
    private static final String LEGACY_ARBITRATION_READ_ONLY_MESSAGE =
            "Legacy arbitration is read-only and no longer supports network messages";

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitrationManager(P2PService p2PService,
                              TradeWalletService tradeWalletService,
                              BtcWalletService walletService,
                              WalletsSetup walletsSetup,
                              TradeManager tradeManager,
                              ClosedTradableManager closedTradableManager,
                              FailedTradesManager failedTradesManager,
                              OpenOfferManager openOfferManager,
                              DaoFacade daoFacade,
                              KeyRing keyRing,
                              ArbitrationDisputeListService arbitrationDisputeListService,
                              Config config,
                              PriceFeedService priceFeedService) {
        super(p2PService, tradeWalletService, walletService, walletsSetup, tradeManager, closedTradableManager, failedTradesManager,
                openOfferManager, daoFacade, keyRing, arbitrationDisputeListService, config, priceFeedService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read-only historical dispute access
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SupportType getSupportType() {
        return SupportType.ARBITRATION;
    }

    @Nullable
    @Override
    public NodeAddress getAgentNodeAddress(Dispute dispute) {
        // TODO: Delete with ArbitrationManager once persisted legacy cases use a read-only historical service.
        return null;
    }

    @Nullable
    @Override
    protected PubKeyRing getExpectedAgentPubKeyRing(Trade trade) {
        // TODO: Delete with ArbitrationManager; legacy arbitration no longer authenticates inbound agent messages.
        return null;
    }

    @Override
    protected Trade.DisputeState getDisputeStateStartedByPeer() {
        // TODO: Delete with ArbitrationManager; no active legacy arbitration state transitions remain.
        return Trade.DisputeState.DISPUTE_STARTED_BY_PEER;
    }

    @Override
    protected AckMessageSourceType getAckMessageSourceType() {
        // TODO: Delete with ArbitrationManager; canProcessAckMessage disables active ACK handling.
        return AckMessageSourceType.ARBITRATION_MESSAGE;
    }

    @Override
    public void cleanupDisputes() {
        // TODO: Delete with ArbitrationManager; persisted legacy arbitration cases are display-only.
    }

    @Override
    protected String getDisputeInfo(Dispute dispute) {
        // TODO: Delete with ArbitrationManager; dispute opening is disabled for legacy arbitration.
        return LEGACY_ARBITRATION_READ_ONLY_MESSAGE;
    }

    @Override
    protected String getDisputeIntroForPeer(String disputeInfo) {
        // TODO: Delete with ArbitrationManager; dispute opening is disabled for legacy arbitration.
        return LEGACY_ARBITRATION_READ_ONLY_MESSAGE;
    }

    @Override
    protected String getDisputeIntroForDisputeCreator(String disputeInfo) {
        // TODO: Delete with ArbitrationManager; dispute opening is disabled for legacy arbitration.
        return LEGACY_ARBITRATION_READ_ONLY_MESSAGE;
    }

    @Override
    protected void addPriceInfoMessage(Dispute dispute, int counter) {
        // TODO: Delete with ArbitrationManager; no active legacy arbitration messages are created.
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Disabled active message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSupportMessage(SupportMessage message, PublicKey senderSignaturePubKey) {
        if (canProcessMessage(message)) {
            log.warn("Ignoring legacy arbitration support message {} for tradeId {} and uid {}. {}.",
                    message.getClass().getSimpleName(),
                    message.getTradeId(),
                    message.getUid(),
                    LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
        }
    }

    @Override
    protected boolean canProcessAckMessage(AckMessage ackMessage) {
        if (ackMessage.getSourceType() == AckMessageSourceType.ARBITRATION_MESSAGE) {
            log.warn("Ignoring legacy arbitration AckMessage for tradeId {} and uid {}. {}.",
                    ackMessage.getSourceId(),
                    ackMessage.getSourceUid(),
                    LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
        }
        return false;
    }

    @Override
    public void onDisputeResultMessage(DisputeResultMessage disputeResultMessage, PublicKey senderSignaturePubKey) {
        log.warn("Ignoring legacy arbitration DisputeResultMessage for tradeId {} and uid {}. {}.",
                disputeResultMessage.getTradeId(),
                disputeResultMessage.getUid(),
                LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
    }

    @Override
    public ChatMessage sendChatMessage(ChatMessage message) {
        log.warn("Not sending legacy arbitration ChatMessage for tradeId {} and uid {}. {}.",
                message.getTradeId(),
                message.getUid(),
                LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
        message.setSendMessageError(LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
        return message;
    }

    @Override
    public void addAndPersistChatMessage(ChatMessage message) {
        log.warn("Ignoring legacy arbitration ChatMessage persistence for tradeId {} and uid {}. {}.",
                message.getTradeId(),
                message.getUid(),
                LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
    }

    @Override
    public void sendOpenNewDisputeMessage(Dispute dispute,
                                          boolean reOpen,
                                          ResultHandler resultHandler,
                                          FaultHandler faultHandler) {
        log.warn("Not opening legacy arbitration dispute for tradeId {}. {}.",
                dispute.getTradeId(),
                LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
        faultHandler.handleFault(LEGACY_ARBITRATION_READ_ONLY_MESSAGE,
                new UnsupportedOperationException(LEGACY_ARBITRATION_READ_ONLY_MESSAGE));
    }

    @Override
    public void sendDisputeOpeningMsg(Dispute dispute) {
        log.warn("Not sending legacy arbitration PeerOpenedDisputeMessage for tradeId {}. {}.",
                dispute.getTradeId(),
                LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
    }

    @Override
    public void sendDisputeResultMessage(DisputeResult disputeResult, Dispute dispute, String summaryText) {
        log.warn("Not sending legacy arbitration DisputeResultMessage for tradeId {}. {}.",
                dispute.getTradeId(),
                LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
    }

    @Override
    public void disputedTradeUpdate(String message, Dispute dispute, boolean close) {
        log.warn("Ignoring legacy arbitration disputed trade update for tradeId {}. {}.",
                dispute.getTradeId(),
                LEGACY_ARBITRATION_READ_ONLY_MESSAGE);
    }
}
