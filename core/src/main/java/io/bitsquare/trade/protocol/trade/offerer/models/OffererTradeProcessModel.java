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
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.trade.TradeProcessModel;
import io.bitsquare.user.User;

import java.io.File;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererTradeProcessModel extends TradeProcessModel implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(OffererTradeProcessModel.class);

    transient private Storage<OffererTradeProcessModel> storage;

    public final Taker taker;
    public final Offerer offerer;

    // written by tasks
    private String takeOfferFeeTxId;

    public OffererTradeProcessModel(Offer offer,
                                    MessageService messageService,
                                    MailboxService mailboxService,
                                    WalletService walletService,
                                    BlockChainService blockChainService,
                                    SignatureService signatureService,
                                    ArbitrationRepository arbitrationRepository,
                                    User user,
                                    File storageDir) {
        super(offer,
                messageService,
                mailboxService,
                walletService,
                blockChainService,
                signatureService,
                arbitrationRepository);

        this.storage = new Storage<>(storageDir);

        OffererTradeProcessModel persisted = storage.initAndGetPersisted(this, getFileName());
        if (persisted != null) {
            log.debug("Model reconstructed form persisted model.");

            setTakeOfferFeeTxId(persisted.takeOfferFeeTxId);

            taker = persisted.taker;
            offerer = persisted.offerer;
        }
        else {
            taker = new Taker();
            offerer = new Offerer();
        }

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

    // Get called form taskRunner after each completed task
    @Override
    public void persist() {
        storage.save();
    }

    @Override
    public void onComplete() {
        // Just in case of successful completion we delete our persisted object
        storage.remove(getFileName());
    }

    public String getTakeOfferFeeTxId() {
        return takeOfferFeeTxId;
    }

    public void setTakeOfferFeeTxId(String takeOfferFeeTxId) {
        this.takeOfferFeeTxId = takeOfferFeeTxId;
    }

    private String getFileName() {
        return getClass().getSimpleName() + "_" + id;
    }
}
