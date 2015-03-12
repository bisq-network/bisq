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

package io.bitsquare.trade.protocol.placeoffer;

import io.bitsquare.btc.WalletService;
import io.bitsquare.offer.Offer;
import io.bitsquare.offer.OfferBookService;
import io.bitsquare.util.tasks.SharedModel;

import org.bitcoinj.core.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceOfferModel extends SharedModel {
    private static final Logger log = LoggerFactory.getLogger(PlaceOfferModel.class);

    private final Offer offer;
    private final WalletService walletService;
    private final OfferBookService offerBookService;
    private Transaction transaction;

    public PlaceOfferModel(Offer offer,
                           WalletService walletService,
                           OfferBookService offerBookService) {
        this.offer = offer;
        this.walletService = walletService;
        this.offerBookService = offerBookService;
    }

    // getter/setter
    public Offer getOffer() {
        return offer;
    }

    public WalletService getWalletService() {
        return walletService;
    }

    public OfferBookService getOfferBookService() {
        return offerBookService;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }

}
