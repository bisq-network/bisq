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
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeSession;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;

import bisq.network.p2p.P2PService;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.crypto.KeyRing;
import bisq.common.storage.Storage;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Singleton
public class MediationManager extends DisputeManager<MediationDisputeList> {

    @Inject
    public MediationManager(P2PService p2PService,
                            TradeWalletService tradeWalletService,
                            BtcWalletService walletService,
                            WalletsSetup walletsSetup,
                            TradeManager tradeManager,
                            ClosedTradableManager closedTradableManager,
                            OpenOfferManager openOfferManager,
                            KeyRing keyRing,
                            Storage<MediationDisputeList> storage) {
        super(p2PService, tradeWalletService, walletService, walletsSetup, tradeManager, closedTradableManager, openOfferManager, keyRing, storage);
    }

    @Override
    protected DisputeSession getConcreteChatSession() {
        return new MediationSession(this);
    }

    @Override
    protected MediationDisputeList getConcreteDisputeList() {
        return new MediationDisputeList(disputeStorage);
    }

    @Override
    // We get that message at both peers. The dispute object is in context of the trader
    public void onDisputeResultMessage(DisputeResultMessage disputeResultMessage) {
        DisputeResult disputeResult = disputeResultMessage.getDisputeResult();
        String tradeId = disputeResult.getTradeId();
        ChatMessage chatMessage = disputeResult.getChatMessage();
        checkNotNull(chatMessage, "chatMessage must not be null");
        Optional<Dispute> disputeOptional = findDispute(disputeResult);
        String uid = disputeResultMessage.getUid();
        if (!disputeOptional.isPresent()) {
            log.warn("We got a dispute result msg but we don't have a matching dispute. " +
                    "That might happen when we get the disputeResultMessage before the dispute was created. " +
                    "We try again after 2 sec. to apply the disputeResultMessage. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                // We delay 2 sec. to be sure the comm. msg gets added first
                Timer timer = UserThread.runAfter(() -> onDisputeResultMessage(disputeResultMessage), 2);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a dispute result msg after we already repeated to apply the message after a delay. " +
                        "That should never happen. TradeId = " + tradeId);
            }
            return;
        }

        Dispute dispute = disputeOptional.get();
        checkArgument(dispute.isMediationDispute(), "Dispute must be MediationDispute");

        cleanupRetryMap(uid);
        if (!dispute.getChatMessages().contains(chatMessage)) {
            dispute.addChatMessage(chatMessage);
        } else {
            log.warn("We got a dispute mail msg what we have already stored. TradeId = " + chatMessage.getTradeId());
        }
        dispute.setIsClosed(true);

        if (dispute.disputeResultProperty().get() != null) {
            log.warn("We got already a dispute result. That should only happen if a dispute needs to be closed " +
                    "again because the first close did not succeed. TradeId = " + tradeId);
        }

        dispute.setDisputeResult(disputeResult);

        Optional<Trade> tradeOptional = tradeManager.getTradeById(tradeId);
        if (tradeOptional.isPresent()) {
            tradeOptional.get().setDisputeState(Trade.DisputeState.MEDIATION_CLOSED);
        } else {
            Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(tradeId);
            openOfferOptional.ifPresent(openOffer -> openOfferManager.closeOpenOffer(openOffer.getOffer()));
        }
        supportManager.sendAckMessage(chatMessage, dispute.getConflictResolverPubKeyRing(), true, null);
    }
}
