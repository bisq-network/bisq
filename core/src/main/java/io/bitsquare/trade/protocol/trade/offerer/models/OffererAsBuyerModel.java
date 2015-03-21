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

import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.SharedTradeModel;
import io.bitsquare.user.User;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererAsBuyerModel extends SharedTradeModel implements Serializable {
    private static final long serialVersionUID = 5000457153390911569L;
    private static final Logger log = LoggerFactory.getLogger(OffererAsBuyerModel.class);

    transient public final Trade trade;
    public final Taker taker;
    public final Offerer offerer;

    // written by tasks
    private String takeOfferFeeTxId;

    public OffererAsBuyerModel(Trade trade,
                               MessageService messageService,
                               MailboxService mailboxService,
                               WalletService walletService,
                               BlockChainService blockChainService,
                               SignatureService signatureService,
                               User user,
                               Persistence persistence) {
        super(trade.getOffer(),
                messageService,
                mailboxService,
                walletService,
                blockChainService,
                signatureService,
                persistence);

        this.trade = trade;

        Serializable serializable = persistence.read(this, "BuyerAsOffererModel_" + id);
        if (serializable instanceof OffererAsBuyerModel) {
            OffererAsBuyerModel persistedModel = (OffererAsBuyerModel) serializable;
            log.debug("Model reconstructed form persisted model.");

            setTakeOfferFeeTxId(persistedModel.takeOfferFeeTxId);

            taker = persistedModel.taker;
            offerer = persistedModel.offerer;
        }
        else {
            taker = new Taker();
            offerer = new Offerer();
        }

        offerer.registrationPubKey = walletService.getRegistrationAddressEntry().getPubKey();
        offerer.registrationKeyPair = walletService.getRegistrationAddressEntry().getKeyPair();
        offerer.addressEntry = walletService.getAddressEntry(id);
        offerer.fiatAccount = user.getBankAccount(offer.getBankAccountId());
        offerer.accountId = user.getAccountId();
        offerer.p2pSigPubKey = user.getP2PSigPubKey();
        offerer.pubKey = offerer.addressEntry.getPubKey();
        log.debug("BuyerAsOffererModel addressEntry " + offerer.addressEntry);
    }

    // Get called form taskRunner after each completed task
    @Override
    public void persist() {
        persistence.write(this, "BuyerAsOffererModel_" + id, this);
    }

    @Override
    public void onComplete() {
        // Just in case of successful completion we delete our persisted object
        persistence.remove(this, "BuyerAsOffererModel_" + id);
    }

    public String getTakeOfferFeeTxId() {
        return takeOfferFeeTxId;
    }

    public void setTakeOfferFeeTxId(String takeOfferFeeTxId) {
        this.takeOfferFeeTxId = takeOfferFeeTxId;
    }
}
