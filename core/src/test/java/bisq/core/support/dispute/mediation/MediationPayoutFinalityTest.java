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
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.config.Config;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.persistence.PersistenceManager;

import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoinj.core.Coin;

import java.io.File;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The mediator's side of the rule that the payout of a mediated trade is final once a result was sent
class MediationPayoutFinalityTest {
    private static final String TRADE_ID = "trade-id";
    private static final NodeAddress MEDIATOR_NODE_ADDRESS = new NodeAddress("mediator.onion", 9999);
    private static final NodeAddress BUYER_NODE_ADDRESS = new NodeAddress("buyer.onion", 9999);
    private static final NodeAddress SELLER_NODE_ADDRESS = new NodeAddress("seller.onion", 9999);

    @TempDir
    private File keyStorageDir;

    private final PubKeyRing buyerPubKeyRing = pubKeyRing();
    private final PubKeyRing sellerPubKeyRing = pubKeyRing();
    private final MailboxMessageService mailboxMessageService = mock(MailboxMessageService.class);
    private MediationDisputeListService disputeListService;
    private MediationManager manager;
    private Dispute buyersTicket;
    private Dispute sellersTicket;

    @BeforeEach
    void setUp() {
        disputeListService = new MediationDisputeListService(persistenceManager());
        manager = mediationManager();
        Contract contract = mock(Contract.class);
        when(contract.getBuyerPubKeyRing()).thenReturn(buyerPubKeyRing);
        when(contract.getSellerPubKeyRing()).thenReturn(sellerPubKeyRing);
        when(contract.getBuyerNodeAddress()).thenReturn(BUYER_NODE_ADDRESS);
        when(contract.getSellerNodeAddress()).thenReturn(SELLER_NODE_ADDRESS);
        buyersTicket = ticket(buyerPubKeyRing, contract);
        sellersTicket = ticket(sellerPubKeyRing, contract);
        disputeListService.getDisputeList().add(buyersTicket);
        disputeListService.getDisputeList().add(sellersTicket);
    }

    @Test
    void resultSentForTheTicketFixesItsPayout() {
        buyersTicket.setDisputeResult(sentResult(buyersTicket, 80_000, 30_000, 2));
        sellersTicket.setDisputeResult(sentResult(sellersTicket, 70_000, 40_000, 1));

        assertSame(buyersTicket.getDisputeResultProperty().get(), manager.findSentResult(buyersTicket).orElseThrow());
    }

    @Test
    void resultSentForTheOtherTicketFixesThePayout() {
        buyersTicket.setDisputeResult(preparedResult(buyersTicket, 70_000, 40_000));
        sellersTicket.setDisputeResult(sentResult(sellersTicket, 80_000, 30_000, 1));

        assertSame(sellersTicket.getDisputeResultProperty().get(), manager.findSentResult(buyersTicket).orElseThrow());
    }

    @Test
    void preparedResultsDoNotFixThePayout() {
        buyersTicket.setDisputeResult(preparedResult(buyersTicket, 70_000, 40_000));
        sellersTicket.setDisputeResult(preparedResult(sellersTicket, 80_000, 30_000));

        assertTrue(manager.findSentResult(buyersTicket).isEmpty());
        assertTrue(manager.hasPayoutOfSentResults(buyersTicket, preparedResult(buyersTicket, 1_000, 109_000)));
    }

    @Test
    void savedPreparedResultsDoNotFixThePayoutAfterARestart() throws InvalidProtocolBufferException {
        // The buyer's ticket was saved with this version, the seller's ticket with an earlier version
        buyersTicket.setDisputeResult(reload(preparedResult(buyersTicket, 70_000, 40_000).toProtoMessage()));
        sellersTicket.setDisputeResult(reload(preparedResult(sellersTicket, 80_000, 30_000).toProtoMessage()
                .toBuilder()
                .setChatMessage(emptyChatMessageOfEarlierVersions())
                .build()));
        DisputeResult buyersResult = buyersTicket.getDisputeResultProperty().get();

        assertTrue(manager.findSentResult(buyersTicket).isEmpty());
        assertTrue(manager.findSentResult(sellersTicket).isEmpty());
        manager.sendDisputeResultMessage(buyersResult, buyersTicket, "summary");

        verify(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    @Test
    void sentResultFixesThePayoutAfterARestart() throws InvalidProtocolBufferException {
        sellersTicket.setDisputeResult(reload(sentResult(sellersTicket, 80_000, 30_000, 1).toProtoMessage()));

        assertSame(sellersTicket.getDisputeResultProperty().get(), manager.findSentResult(buyersTicket).orElseThrow());
        assertFalse(manager.hasPayoutOfSentResults(buyersTicket, preparedResult(buyersTicket, 70_000, 40_000)));
    }

    @Test
    void refusesToSendAnotherPayoutToTheOtherTrader() {
        sellersTicket.setDisputeResult(sentResult(sellersTicket, 80_000, 30_000, 1));
        DisputeResult changedResult = preparedResult(buyersTicket, 70_000, 40_000);

        assertFalse(manager.hasPayoutOfSentResults(buyersTicket, changedResult));
        assertThrows(IllegalArgumentException.class,
                () -> manager.sendDisputeResultMessage(changedResult, buyersTicket, "summary"));

        assertNull(changedResult.getChatMessage());
        assertTrue(buyersTicket.getChatMessages().isEmpty());
        verify(mailboxMessageService, never()).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    @Test
    void refusesToSendAnotherPayoutToTheSameTraderAgain() {
        buyersTicket.setDisputeResult(sentResult(buyersTicket, 80_000, 30_000, 1));
        DisputeResult changedResult = preparedResult(buyersTicket, 70_000, 40_000);

        assertThrows(IllegalArgumentException.class,
                () -> manager.sendDisputeResultMessage(changedResult, buyersTicket, "summary"));

        verify(mailboxMessageService, never()).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    @Test
    void sendsTheSamePayoutToTheOtherTrader() {
        sellersTicket.setDisputeResult(sentResult(sellersTicket, 80_000, 30_000, 1));
        DisputeResult sameResult = preparedResult(buyersTicket, 80_000, 30_000);

        manager.sendDisputeResultMessage(sameResult, buyersTicket, "summary");

        assertTrue(buyersTicket.getChatMessages().contains(sameResult.getChatMessage()));
        verify(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    private MediationManager mediationManager() {
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
        when(p2PService.getAddress()).thenReturn(MEDIATOR_NODE_ADDRESS);
        ClosedTradableManager closedTradableManager = mock(ClosedTradableManager.class);
        FailedTradesManager failedTradesManager = mock(FailedTradesManager.class);
        TradeManager tradeManager = mock(TradeManager.class);
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.empty());
        return new MediationManager(p2PService,
                mock(TradeWalletService.class),
                mock(BtcWalletService.class),
                mock(WalletsSetup.class),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                mock(OpenOfferManager.class),
                mock(DaoFacade.class),
                new KeyRing(new KeyStorage(keyStorageDir)),
                disputeListService,
                mock(Config.class),
                mock(PriceFeedService.class));
    }

    private static Dispute ticket(PubKeyRing traderPubKeyRing, Contract contract) {
        return new Dispute(0,
                TRADE_ID,
                traderPubKeyRing.hashCode(),
                true,
                true,
                traderPubKeyRing,
                0,
                0,
                contract,
                null,
                null,
                null,
                null,
                null,
                "",
                null,
                null,
                pubKeyRing(),
                false,
                SupportType.MEDIATION);
    }

    private static DisputeResult preparedResult(Dispute ticket, long buyerPayoutAmount, long sellerPayoutAmount) {
        DisputeResult result = new DisputeResult(TRADE_ID, ticket.getTraderId());
        result.setBuyerPayoutAmount(Coin.valueOf(buyerPayoutAmount));
        result.setSellerPayoutAmount(Coin.valueOf(sellerPayoutAmount));
        return result;
    }

    private static DisputeResult sentResult(Dispute ticket,
                                            long buyerPayoutAmount,
                                            long sellerPayoutAmount,
                                            long closeDate) {
        DisputeResult result = preparedResult(ticket, buyerPayoutAmount, sellerPayoutAmount);
        result.setCloseDate(new Date(closeDate));
        result.setChatMessage(new ChatMessage(SupportType.MEDIATION,
                TRADE_ID,
                ticket.getTraderId(),
                false,
                "result",
                MEDIATOR_NODE_ADDRESS));
        return result;
    }

    private static DisputeResult reload(protobuf.DisputeResult storedResult) throws InvalidProtocolBufferException {
        return DisputeResult.fromProto(protobuf.DisputeResult.parseFrom(storedResult.toByteArray()));
    }

    // Earlier versions read an absent chat message as the default instance and stored it with the result again
    private static protobuf.ChatMessage emptyChatMessageOfEarlierVersions() {
        return ChatMessage.fromPayloadProto(protobuf.ChatMessage.getDefaultInstance())
                .toProtoNetworkEnvelope()
                .getChatMessage();
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
    }

    @SuppressWarnings("unchecked")
    private static PersistenceManager<MediationDisputeList> persistenceManager() {
        return mock(PersistenceManager.class);
    }
}
