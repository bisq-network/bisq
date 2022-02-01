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

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.arbitration.messages.PeerPublishedDisputePayoutTxMessage;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.dispute.messages.OpenNewDisputeMessage;
import bisq.core.support.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Singleton
public final class ArbitrationManager extends DisputeManager<ArbitrationDisputeList> {

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
                              OpenOfferManager openOfferManager,
                              DaoFacade daoFacade,
                              KeyRing keyRing,
                              ArbitrationDisputeListService arbitrationDisputeListService,
                              Config config,
                              PriceFeedService priceFeedService) {
        super(p2PService, tradeWalletService, walletService, walletsSetup, tradeManager, closedTradableManager,
                openOfferManager, daoFacade, keyRing, arbitrationDisputeListService, config, priceFeedService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement template methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SupportType getSupportType() {
        return SupportType.ARBITRATION;
    }

    @Override
    public void onSupportMessage(SupportMessage message) {
        if (canProcessMessage(message)) {
            log.info("Received {} with tradeId {} and uid {}",
                    message.getClass().getSimpleName(), message.getTradeId(), message.getUid());

            if (message instanceof OpenNewDisputeMessage) {
                onOpenNewDisputeMessage((OpenNewDisputeMessage) message);
            } else if (message instanceof PeerOpenedDisputeMessage) {
                onPeerOpenedDisputeMessage((PeerOpenedDisputeMessage) message);
            } else if (message instanceof ChatMessage) {
                onChatMessage((ChatMessage) message);
            } else if (message instanceof DisputeResultMessage) {
                onDisputeResultMessage((DisputeResultMessage) message);
            } else if (message instanceof PeerPublishedDisputePayoutTxMessage) {
                onDisputedPayoutTxMessage((PeerPublishedDisputePayoutTxMessage) message);
            } else {
                log.warn("Unsupported message at dispatchMessage. message={}", message);
            }
        }
    }

    @Nullable
    @Override
    public NodeAddress getAgentNodeAddress(Dispute dispute) {
        return null;
    }

    @Override
    protected Trade.DisputeState getDisputeStateStartedByPeer() {
        return Trade.DisputeState.DISPUTE_STARTED_BY_PEER;
    }

    @Override
    protected AckMessageSourceType getAckMessageSourceType() {
        return AckMessageSourceType.ARBITRATION_MESSAGE;
    }

    @Override
    public void cleanupDisputes() {
        disputeListService.cleanupDisputes(tradeId -> tradeManager.closeDisputedTrade(tradeId, Trade.DisputeState.DISPUTE_CLOSED));
    }

    @Override
    protected String getDisputeInfo(Dispute dispute) {
        String role = Res.get("shared.arbitrator").toLowerCase();
        String link = "https://bisq.wiki/Arbitrator#Arbitrator_versus_Legacy_Arbitrator";
        return Res.get("support.initialInfo", role, "", role, link);        // Arbitration is not used anymore
    }

    @Override
    protected String getDisputeIntroForPeer(String disputeInfo) {
        return Res.get("support.peerOpenedDispute", disputeInfo, Version.VERSION);
    }

    @Override
    protected String getDisputeIntroForDisputeCreator(String disputeInfo) {
        return Res.get("support.youOpenedDispute", disputeInfo, Version.VERSION);
    }

    @Override
    protected void addPriceInfoMessage(Dispute dispute, int counter) {
        // Arbitrator is not used anymore.
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    // We get that message at both peers. The dispute object is in context of the trader
    public void onDisputeResultMessage(DisputeResultMessage disputeResultMessage) {
        DisputeResult disputeResult = disputeResultMessage.getDisputeResult();
        ChatMessage chatMessage = disputeResult.getChatMessage();
        checkNotNull(chatMessage, "chatMessage must not be null");
        if (Arrays.equals(disputeResult.getArbitratorPubKey(),
                btcWalletService.getArbitratorAddressEntry().getPubKey())) {
            log.error("Arbitrator received disputeResultMessage. That must never happen.");
            return;
        }

        String tradeId = disputeResult.getTradeId();
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
        cleanupRetryMap(uid);
        if (!dispute.getChatMessages().contains(chatMessage)) {
            dispute.addAndPersistChatMessage(chatMessage);
        } else {
            log.warn("We got a dispute mail msg what we have already stored. TradeId = " + chatMessage.getTradeId());
        }
        dispute.setIsClosed();

        if (dispute.disputeResultProperty().get() != null) {
            log.warn("We already got a dispute result. That should only happen if a dispute needs to be closed " +
                    "again because the first close did not succeed. TradeId = " + tradeId);
        }

        dispute.setDisputeResult(disputeResult);
        Optional<Trade> tradeOptional = tradeManager.getTradeById(tradeId);
        String errorMessage = null;
        boolean success = false;
        try {
            // We need to avoid publishing the tx from both traders as it would create problems with zero confirmation withdrawals
            // There would be different transactions if both sign and publish (signers: once buyer+arb, once seller+arb)
            // The tx publisher is the winner or in case both get 50% the buyer, as the buyer has more inventive to publish the tx as he receives
            // more BTC as he has deposited
            Contract contract = dispute.getContract();

            boolean isBuyer = pubKeyRing.equals(contract.getBuyerPubKeyRing());
            DisputeResult.Winner publisher = disputeResult.getWinner();

            // Sometimes the user who receives the trade amount is never online, so we might want to
            // let the loser publish the tx. When the winner comes online he gets his funds as it was published by the other peer.
            // Default isLoserPublisher is set to false
            if (disputeResult.isLoserPublisher()) {
                // we invert the logic
                if (publisher == DisputeResult.Winner.BUYER)
                    publisher = DisputeResult.Winner.SELLER;
                else if (publisher == DisputeResult.Winner.SELLER)
                    publisher = DisputeResult.Winner.BUYER;
            }

            if ((isBuyer && publisher == DisputeResult.Winner.BUYER)
                    || (!isBuyer && publisher == DisputeResult.Winner.SELLER)) {

                Transaction payoutTx = null;
                if (tradeOptional.isPresent()) {
                    payoutTx = tradeOptional.get().getPayoutTx();
                } else {
                    Optional<Tradable> tradableOptional = closedTradableManager.getTradableById(tradeId);
                    if (tradableOptional.isPresent() && tradableOptional.get() instanceof Trade) {
                        payoutTx = ((Trade) tradableOptional.get()).getPayoutTx();
                    }
                }

                if (payoutTx == null) {
                    if (dispute.getDepositTxSerialized() != null) {
                        byte[] multiSigPubKey = isBuyer ? contract.getBuyerMultiSigPubKey() : contract.getSellerMultiSigPubKey();
                        DeterministicKey multiSigKeyPair = btcWalletService.getMultiSigKeyPair(tradeId, multiSigPubKey);
                        Transaction signedDisputedPayoutTx = tradeWalletService.traderSignAndFinalizeDisputedPayoutTx(
                                dispute.getDepositTxSerialized(),
                                disputeResult.getArbitratorSignature(),
                                disputeResult.getBuyerPayoutAmount(),
                                disputeResult.getSellerPayoutAmount(),
                                contract.getBuyerPayoutAddressString(),
                                contract.getSellerPayoutAddressString(),
                                multiSigKeyPair,
                                contract.getBuyerMultiSigPubKey(),
                                contract.getSellerMultiSigPubKey(),
                                disputeResult.getArbitratorPubKey()
                        );
                        Transaction committedDisputedPayoutTx = WalletService.maybeAddSelfTxToWallet(signedDisputedPayoutTx, btcWalletService.getWallet());
                        tradeWalletService.broadcastTx(committedDisputedPayoutTx, new TxBroadcaster.Callback() {
                            @Override
                            public void onSuccess(Transaction transaction) {
                                // after successful publish we send peer the tx
                                dispute.setDisputePayoutTxId(transaction.getTxId().toString());
                                sendPeerPublishedPayoutTxMessage(transaction, dispute, contract);
                                updateTradeOrOpenOfferManager(tradeId);
                            }

                            @Override
                            public void onFailure(TxBroadcastException exception) {
                                log.error(exception.getMessage());
                            }
                        }, 15);

                        success = true;
                    } else {
                        errorMessage = "DepositTx is null. TradeId = " + tradeId;
                        log.warn(errorMessage);
                        success = false;
                    }
                } else {
                    log.warn("We already got a payout tx. That might be the case if the other peer did not get the " +
                            "payout tx and opened a dispute. TradeId = " + tradeId);
                    dispute.setDisputePayoutTxId(payoutTx.getTxId().toString());
                    sendPeerPublishedPayoutTxMessage(payoutTx, dispute, contract);

                    success = true;
                }
            } else {
                log.trace("We don't publish the tx as we are not the winning party.");
                // Clean up tangling trades
                if (dispute.disputeResultProperty().get() != null && dispute.isClosed()) {
                    updateTradeOrOpenOfferManager(tradeId);
                }

                success = true;
            }
        } catch (TransactionVerificationException e) {
            errorMessage = "Error at traderSignAndFinalizeDisputedPayoutTx " + e.toString();
            log.error(errorMessage, e);
            success = false;

            // We prefer to close the dispute in that case. If there was no deposit tx and a random tx was used
            // we get a TransactionVerificationException. No reason to keep that dispute open...
            updateTradeOrOpenOfferManager(tradeId);

            throw new RuntimeException(errorMessage);
        } catch (AddressFormatException | WalletException | SignatureDecodeException e) {
            errorMessage = "Error at traderSignAndFinalizeDisputedPayoutTx " + e.toString();
            log.error(errorMessage, e);
            success = false;
            throw new RuntimeException(errorMessage);
        } finally {
            // We use the chatMessage as we only persist those not the disputeResultMessage.
            // If we would use the disputeResultMessage we could not lookup for the msg when we receive the AckMessage.
            sendAckMessage(chatMessage, dispute.getAgentPubKeyRing(), success, errorMessage);
        }

        maybeClearSensitiveData();
        requestPersistence();
    }

    // Losing trader or in case of 50/50 the seller gets the tx sent from the winner or buyer
    private void onDisputedPayoutTxMessage(PeerPublishedDisputePayoutTxMessage peerPublishedDisputePayoutTxMessage) {
        String uid = peerPublishedDisputePayoutTxMessage.getUid();
        String tradeId = peerPublishedDisputePayoutTxMessage.getTradeId();
        Optional<Dispute> disputeOptional = findOwnDispute(tradeId);
        if (!disputeOptional.isPresent()) {
            log.debug("We got a peerPublishedPayoutTxMessage but we don't have a matching dispute. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                // We delay 3 sec. to be sure the close msg gets added first
                Timer timer = UserThread.runAfter(() -> onDisputedPayoutTxMessage(peerPublishedDisputePayoutTxMessage), 3);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a peerPublishedPayoutTxMessage after we already repeated to apply the message after a delay. " +
                        "That should never happen. TradeId = " + tradeId);
            }
            return;
        }

        Dispute dispute = disputeOptional.get();
        Contract contract = dispute.getContract();
        boolean isBuyer = pubKeyRing.equals(contract.getBuyerPubKeyRing());
        PubKeyRing peersPubKeyRing = isBuyer ? contract.getSellerPubKeyRing() : contract.getBuyerPubKeyRing();

        cleanupRetryMap(uid);

        Transaction committedDisputePayoutTx = WalletService.maybeAddNetworkTxToWallet(peerPublishedDisputePayoutTxMessage.getTransaction(), btcWalletService.getWallet());

        dispute.setDisputePayoutTxId(committedDisputePayoutTx.getTxId().toString());
        BtcWalletService.printTx("Disputed payoutTx received from peer", committedDisputePayoutTx);

        // We can only send the ack msg if we have the peersPubKeyRing which requires the dispute
        sendAckMessage(peerPublishedDisputePayoutTxMessage, peersPubKeyRing, true, null);
        requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send messages
    ///////////////////////////////////////////////////////////////////////////////////////////

    // winner (or buyer in case of 50/50) sends tx to other peer
    private void sendPeerPublishedPayoutTxMessage(Transaction transaction, Dispute dispute, Contract contract) {
        PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contract.getSellerPubKeyRing() : contract.getBuyerPubKeyRing();
        NodeAddress peersNodeAddress = dispute.isDisputeOpenerIsBuyer() ? contract.getSellerNodeAddress() : contract.getBuyerNodeAddress();
        log.trace("sendPeerPublishedPayoutTxMessage to peerAddress {}", peersNodeAddress);
        PeerPublishedDisputePayoutTxMessage message = new PeerPublishedDisputePayoutTxMessage(transaction.bitcoinSerialize(),
                dispute.getTradeId(),
                p2PService.getAddress(),
                UUID.randomUUID().toString(),
                getSupportType());
        log.info("Send {} to peer {}. tradeId={}, uid={}",
                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
        mailboxMessageService.sendEncryptedMailboxMessage(peersNodeAddress,
                peersPubKeyRing,
                message,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("{} stored in mailbox for peer {}. tradeId={}, uid={}",
                                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid(), errorMessage);
                    }
                }
        );
    }

    private void updateTradeOrOpenOfferManager(String tradeId) {
        // set state after payout as we call swapTradeEntryToAvailableEntry
        if (tradeManager.getTradeById(tradeId).isPresent()) {
            tradeManager.closeDisputedTrade(tradeId, Trade.DisputeState.DISPUTE_CLOSED);
        } else {
            Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(tradeId);
            openOfferOptional.ifPresent(openOffer -> openOfferManager.closeOpenOffer(openOffer.getOffer()));
        }
    }
}
