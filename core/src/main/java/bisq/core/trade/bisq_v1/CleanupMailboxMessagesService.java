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

package bisq.core.trade.bisq_v1;

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessage;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

//TODO with the redesign of mailbox messages that is not required anymore. We leave it for now as we want to minimize
// changes for the 1.5.0 release but we should clean up afterwards...

/**
 * Util for removing pending mailbox messages in case the trade has been closed by the seller after confirming receipt
 * and a AckMessage as mailbox message will be sent by the buyer once they go online. In that case the seller's trade
 * is closed already and the TradeProtocol is not executing the message processing, thus the mailbox message would not
 * be removed. To ensure that in such cases (as well other potential cases in failure scenarios) the mailbox message
 * gets removed from the network we use that util.
 *
 * This class must not be injected as a singleton!
 */
@Slf4j
public class CleanupMailboxMessagesService {
    private final P2PService p2PService;
    private final MailboxMessageService mailboxMessageService;

    @Inject
    public CleanupMailboxMessagesService(P2PService p2PService, MailboxMessageService mailboxMessageService) {
        this.p2PService = p2PService;
        this.mailboxMessageService = mailboxMessageService;
    }

    public void handleTrades(List<Trade> trades) {
        // We wrap in a try catch as in failed trades we cannot be sure if expected data is set, so we could get
        // a NullPointer and do not want that this escalate to the user.
        try {
            if (p2PService.isBootstrapped()) {
                cleanupMailboxMessages(trades);
            } else {
                p2PService.addP2PServiceListener(new BootstrapListener() {
                    @Override
                    public void onUpdatedDataReceived() {
                        cleanupMailboxMessages(trades);
                    }
                });
            }
        } catch (Throwable t) {
            log.error("Cleanup mailbox messages failed. {}", t.toString());
        }
    }

    private void cleanupMailboxMessages(List<Trade> trades) {
        mailboxMessageService.getMyDecryptedMailboxMessages()
                .forEach(message -> handleDecryptedMessageWithPubKey(message, trades));
    }

    private void handleDecryptedMessageWithPubKey(DecryptedMessageWithPubKey decryptedMessageWithPubKey,
                                                  List<Trade> trades) {
        trades.stream()
                .filter(trade -> isMessageForTrade(decryptedMessageWithPubKey, trade))
                .filter(trade -> isPubKeyValid(decryptedMessageWithPubKey, trade))
                .filter(trade -> decryptedMessageWithPubKey.getNetworkEnvelope() instanceof MailboxMessage)
                .forEach(trade -> removeEntryFromMailbox((MailboxMessage) decryptedMessageWithPubKey.getNetworkEnvelope(), trade));
    }

    private boolean isMessageForTrade(DecryptedMessageWithPubKey decryptedMessageWithPubKey, Trade trade) {
        NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
        if (networkEnvelope instanceof TradeMessage) {
            return isMyMessage((TradeMessage) networkEnvelope, trade);
        } else if (networkEnvelope instanceof AckMessage) {
            return isMyMessage((AckMessage) networkEnvelope, trade);
        }
        // Instance must be TradeMessage or AckMessage.
        return false;
    }

    private void removeEntryFromMailbox(MailboxMessage mailboxMessage, Trade trade) {
        log.info("We found a pending mailbox message ({}) for trade {}. " +
                        "As the trade is closed we remove the mailbox message.",
                mailboxMessage.getClass().getSimpleName(), trade.getId());
        mailboxMessageService.removeMailboxMsg(mailboxMessage);
    }

    private boolean isMyMessage(TradeMessage message, Trade trade) {
        return message.getTradeId().equals(trade.getId());
    }

    private boolean isMyMessage(AckMessage ackMessage, Trade trade) {
        return ackMessage.getSourceType() == AckMessageSourceType.TRADE_MESSAGE &&
                ackMessage.getSourceId().equals(trade.getId());
    }

    private boolean isPubKeyValid(DecryptedMessageWithPubKey decryptedMessageWithPubKey, Trade trade) {
        // We can only validate the peers pubKey if we have it already. If we are the taker we get it from the offer
        // Otherwise it depends on the state of the trade protocol if we have received the peers pubKeyRing already.
        PubKeyRing peersPubKeyRing = trade.getProcessModel().getTradePeer().getPubKeyRing();
        boolean isValid = true;
        if (peersPubKeyRing != null &&
                !decryptedMessageWithPubKey.getSignaturePubKey().equals(peersPubKeyRing.getSignaturePubKey())) {
            isValid = false;
            log.warn("SignaturePubKey in decryptedMessageWithPubKey does not match the SignaturePubKey we have set for our trading peer.");
        }
        return isValid;
    }
}
