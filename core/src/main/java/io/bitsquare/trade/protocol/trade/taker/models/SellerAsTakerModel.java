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

package io.bitsquare.trade.protocol.trade.taker.models;

import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.network.Peer;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.protocol.trade.OfferSharedModel;
import io.bitsquare.trade.protocol.trade.offerer.models.TakerModel;
import io.bitsquare.user.User;

import org.bitcoinj.core.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerAsTakerModel extends OfferSharedModel {
    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerModel.class);

    private final Trade trade;
    public final TakerModel taker;
    public final OffererModel offerer;
    
    private Transaction publishedDepositTx;
    private Transaction takeOfferFeeTx;
    private Transaction payoutTx;

    public SellerAsTakerModel(Trade trade,
                              Peer offererPeer,
                              TradeMessageService tradeMessageService,
                              WalletService walletService,
                              BlockChainService blockChainService,
                              SignatureService signatureService,
                              User user,
                              Persistence persistence) {
        super(trade.getOffer(),
                tradeMessageService,
                walletService,
                blockChainService,
                signatureService,
                user,
                persistence);

        this.trade = trade;

        taker = new TakerModel();
        offerer = new OffererModel(offererPeer);

        taker.pubKey = getAddressEntry().getPubKey();
    }


    public Trade getTrade() {
        return trade;
    }

    public Transaction getPublishedDepositTx() {
        return publishedDepositTx;
    }

    public void setPublishedDepositTx(Transaction publishedDepositTx) {
        this.publishedDepositTx = publishedDepositTx;
    }

    public Transaction getTakeOfferFeeTx() {
        return takeOfferFeeTx;
    }

    public void setTakeOfferFeeTx(Transaction takeOfferFeeTx) {
        this.takeOfferFeeTx = takeOfferFeeTx;
    }

    public void setDepositTx(Transaction transaction) {
        
    }

    public Transaction getPayoutTx() {
        return payoutTx;
    }

    public void setPayoutTx(Transaction payoutTx) {
        this.payoutTx = payoutTx;
    }
}
