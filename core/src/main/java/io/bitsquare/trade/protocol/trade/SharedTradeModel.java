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

import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.SharedTaskModel;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedTradeModel extends SharedTaskModel implements Serializable {
    private static final long serialVersionUID = -2523252022571497157L;
    protected static final Logger log = LoggerFactory.getLogger(SharedTradeModel.class);

    // provided
    transient public final Offer offer;
    transient public final MessageService messageService;
    transient public final MailboxService mailboxService;
    transient public final WalletService walletService;
    transient public final BlockChainService blockChainService;
    transient public final SignatureService signatureService;

    transient public MailboxMessage mailboxMessage;

    // derived
    transient public final String id;
    transient public final TradeWalletService tradeWalletService;
    transient public final byte[] arbitratorPubKey;

    // data written/read by tasks
    transient private TradeMessage tradeMessage;

    protected SharedTradeModel(Offer offer,
                               MessageService messageService,
                               MailboxService mailboxService,
                               WalletService walletService,
                               BlockChainService blockChainService,
                               SignatureService signatureService) {
        this.offer = offer;
        this.messageService = messageService;
        this.mailboxService = mailboxService;
        this.walletService = walletService;
        this.blockChainService = blockChainService;
        this.signatureService = signatureService;

        id = offer.getId();
        tradeWalletService = walletService.getTradeWalletService();
        //TODO use default arbitrator for now
        arbitratorPubKey = offer.getArbitrators().get(0).getPubKey();
    }

    public void setTradeMessage(TradeMessage tradeMessage) {
        this.tradeMessage = tradeMessage;
    }

    public TradeMessage getTradeMessage() {
        return tradeMessage;
    }

}
