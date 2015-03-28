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
import io.bitsquare.p2p.MessageService;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.user.User;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessModel extends Model implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ProcessModel.class);

    // Transient/Immutable
    transient private MessageService messageService;
    transient private WalletService walletService;
    transient private TradeWalletService tradeWalletService;
    transient private BlockChainService blockChainService;
    transient private SignatureService signatureService;
    transient private ArbitrationRepository arbitrationRepository;
    transient private Offer offer;

    // Mutable
    transient private MailboxMessage mailboxMessage;
    transient private TradeMessage tradeMessage;


    protected ProcessModel() {
        log.trace("Created by constructor");
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");
    }

    public void onAllServicesInitialized(Offer offer,
                                         MessageService messageService,
                                         WalletService walletService,
                                         TradeWalletService tradeWalletService,
                                         BlockChainService blockChainService,
                                         SignatureService signatureService,
                                         ArbitrationRepository arbitrationRepository,
                                         User user) {
        this.offer = offer;
        this.messageService = messageService;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.blockChainService = blockChainService;
        this.signatureService = signatureService;
        this.arbitrationRepository = arbitrationRepository;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter only
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MessageService getMessageService() {
        return messageService;
    }

    public WalletService getWalletService() {
        return walletService;
    }

    public TradeWalletService getTradeWalletService() {
        return tradeWalletService;
    }

    public BlockChainService getBlockChainService() {
        return blockChainService;
    }

    public SignatureService getSignatureService() {
        return signatureService;
    }

    public byte[] getArbitratorPubKey() {
        return arbitrationRepository.getDefaultArbitrator().getPubKey();
    }

    public Offer getOffer() {
        return offer;
    }

    public String getId() {
        return offer.getId();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTradeMessage(TradeMessage tradeMessage) {
        this.tradeMessage = tradeMessage;
    }

    @Nullable
    public TradeMessage getTradeMessage() {
        return tradeMessage;
    }

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        this.mailboxMessage = mailboxMessage;
    }

    @Nullable
    public MailboxMessage getMailboxMessage() {
        return mailboxMessage;
    }


    @Override
    public String toString() {
        return "ProcessModel{" +
                "offer=" + offer +
                '}';
    }
}
