/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.arbitration.ArbitrationRepository;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.user.User;

import java.io.Serializable;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeProcessModel extends Model implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    protected static final Logger log = LoggerFactory.getLogger(TradeProcessModel.class);

    // those fields are re-assigned in case of deserialized object after backend is ready.
    // Therefore they are not final but annotated with @NotNull 
    @NotNull transient public MessageService messageService;
    @NotNull transient public MailboxService mailboxService;
    @NotNull transient public WalletService walletService;
    @NotNull transient public TradeWalletService tradeWalletService;
    @NotNull transient public BlockChainService blockChainService;
    @NotNull transient public SignatureService signatureService;

    @NotNull public Offer offer;
    @NotNull public String id;
    @NotNull public byte[] arbitratorPubKey;

    @Nullable private transient MailboxMessage mailboxMessage;
    @Nullable transient private TradeMessage tradeMessage;

    protected TradeProcessModel() {
    }

    public void init(@NotNull Offer offer,
                     @NotNull MessageService messageService,
                     @NotNull MailboxService mailboxService,
                     @NotNull WalletService walletService,
                     @NotNull TradeWalletService tradeWalletService,
                     @NotNull BlockChainService blockChainService,
                     @NotNull SignatureService signatureService,
                     @NotNull ArbitrationRepository arbitrationRepository,
                     @NotNull User user) {
        this.offer = offer;
        this.messageService = messageService;
        this.mailboxService = mailboxService;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.blockChainService = blockChainService;
        this.signatureService = signatureService;

        id = offer.getId();
        arbitratorPubKey = arbitrationRepository.getDefaultArbitrator().getPubKey();
        assert arbitratorPubKey != null;
    }

    public void setTradeMessage(@NotNull TradeMessage tradeMessage) {
        this.tradeMessage = tradeMessage;
    }

    public void setMailboxMessage(@NotNull MailboxMessage mailboxMessage) {
        this.mailboxMessage = mailboxMessage;
    }

    @Nullable
    public TradeMessage getTradeMessage() {
        return tradeMessage;
    }

    @Nullable
    public MailboxMessage getMailboxMessage() {
        return mailboxMessage;
    }
}
