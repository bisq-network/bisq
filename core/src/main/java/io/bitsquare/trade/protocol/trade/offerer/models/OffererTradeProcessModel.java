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

package io.bitsquare.trade.protocol.trade.offerer.models;

import io.bitsquare.arbitration.ArbitrationRepository;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.trade.protocol.trade.TradeProcessModel;
import io.bitsquare.user.User;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererTradeProcessModel extends TradeProcessModel implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(OffererTradeProcessModel.class);

    public final Taker taker = new Taker();
    public final Offerer offerer = new Offerer();

    // written by tasks
    private String takeOfferFeeTxId;

    public OffererTradeProcessModel(Offer offer,
                                    MessageService messageService,
                                    MailboxService mailboxService,
                                    WalletService walletService,
                                    BlockChainService blockChainService,
                                    SignatureService signatureService,
                                    ArbitrationRepository arbitrationRepository,
                                    User user) {
        super(offer,
                messageService,
                mailboxService,
                walletService,
                blockChainService,
                signatureService,
                arbitrationRepository);

        offerer.registrationPubKey = walletService.getRegistrationAddressEntry().getPubKey();
        offerer.registrationKeyPair = walletService.getRegistrationAddressEntry().getKeyPair();
        offerer.addressEntry = walletService.getAddressEntry(id);
        offerer.fiatAccount = user.getFiatAccount(offer.getBankAccountId());
        offerer.accountId = user.getAccountId();
        offerer.p2pSigPubKey = user.getP2PSigPubKey();
        offerer.p2pEncryptPubKey = user.getP2PEncryptPubKey();
        offerer.tradeWalletPubKey = offerer.addressEntry.getPubKey();
        log.debug("BuyerAsOffererModel addressEntry " + offerer.addressEntry);
    }

    public String getTakeOfferFeeTxId() {
        return takeOfferFeeTxId;
    }

    public void setTakeOfferFeeTxId(String takeOfferFeeTxId) {
        this.takeOfferFeeTxId = takeOfferFeeTxId;
    }
}
