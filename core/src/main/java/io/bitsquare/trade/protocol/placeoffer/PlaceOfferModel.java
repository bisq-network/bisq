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

import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OfferBookService;

import org.bitcoinj.core.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceOfferModel implements Model {
    private static final Logger log = LoggerFactory.getLogger(PlaceOfferModel.class);

    public final Offer offer;
    public final WalletService walletService;
    public final TradeWalletService tradeWalletService;
    public final OfferBookService offerBookService;
    public boolean offerAddedToOfferBook;
    private Transaction transaction;

    public PlaceOfferModel(Offer offer,
                           WalletService walletService,
                           TradeWalletService tradeWalletService,
                           OfferBookService offerBookService) {
        this.offer = offer;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.offerBookService = offerBookService;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    @Override
    public void persist() {

    }

    @Override
    public void onComplete() {

    }
}
