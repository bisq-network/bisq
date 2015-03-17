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

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.SharedModel;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.offer.Offer;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.user.User;

import org.bitcoinj.crypto.DeterministicKey;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfferSharedModel extends SharedModel {
    protected static final Logger log = LoggerFactory.getLogger(OfferSharedModel.class);

    // provided
    private final Offer offer;
    private final TradeMessageService tradeMessageService;
    private final WalletService walletService;
    private final BlockChainService blockChainService;
    private final SignatureService signatureService;
    private Persistence persistence;


    // derived
    private final String id;
    private final FiatAccount fiatAccount;
    private final String accountId;
    private final PublicKey networkPubKey;
    private final byte[] registrationPubKey;
    private final DeterministicKey registrationKeyPair;
    private final byte[] arbitratorPubKey;
    private final AddressEntry addressEntry;
    private final TradeWalletService tradeWalletService;

    // data written/read by tasks
    private TradeMessage tradeMessage;


    protected OfferSharedModel(Offer offer,
                               TradeMessageService tradeMessageService,
                               WalletService walletService,
                               BlockChainService blockChainService,
                               SignatureService signatureService,
                               User user,
                               Persistence persistence) {
        this.offer = offer;
        this.tradeMessageService = tradeMessageService;
        this.walletService = walletService;
        this.blockChainService = blockChainService;
        this.signatureService = signatureService;
        this.persistence = persistence;

        id = offer.getId();
        tradeWalletService = walletService.getTradeWalletService();
        //TODO use default arbitrator for now
        arbitratorPubKey = offer.getArbitrators().get(0).getPubKey();
        registrationPubKey = walletService.getRegistrationAddressEntry().getPubKey();
        registrationKeyPair = walletService.getRegistrationAddressEntry().getKeyPair();
        addressEntry = walletService.getAddressEntry(id);
        fiatAccount = user.getBankAccount(offer.getBankAccountId());
        accountId = user.getAccountId();
        networkPubKey = user.getNetworkPubKey();
    }

    //setter
    public void setTradeMessage(TradeMessage tradeMessage) {
        this.tradeMessage = tradeMessage;
    }

    // getter
    public String getId() {
        return id;
    }

    public TradeWalletService getTradeWalletService() {
        return tradeWalletService;
    }

    public TradeMessage getTradeMessage() {
        return tradeMessage;
    }

    public Offer getOffer() {
        return offer;
    }

    public TradeMessageService getTradeMessageService() {
        return tradeMessageService;
    }

    public WalletService getWalletService() {
        return walletService;
    }

    public BlockChainService getBlockChainService() {
        return blockChainService;
    }

    public SignatureService getSignatureService() {
        return signatureService;
    }

    public FiatAccount getFiatAccount() {
        return fiatAccount;
    }

    public String getAccountId() {
        return accountId;
    }

    public PublicKey getNetworkPubKey() {
        return networkPubKey;
    }

    public byte[] getRegistrationPubKey() {
        return registrationPubKey;
    }

    public DeterministicKey getRegistrationKeyPair() {
        return registrationKeyPair;
    }

    public byte[] getArbitratorPubKey() {
        return arbitratorPubKey;
    }

    public AddressEntry getAddressEntry() {
        return addressEntry;
    }

}
