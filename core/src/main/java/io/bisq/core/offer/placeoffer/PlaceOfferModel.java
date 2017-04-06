/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.offer.placeoffer;

import io.bisq.common.taskrunner.Model;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferBookService;
import io.bisq.core.user.User;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceOfferModel implements Model {
    private static final Logger log = LoggerFactory.getLogger(PlaceOfferModel.class);

    public final Offer offer;
    public final Coin reservedFundsForOffer;
    public final Coin makerFee;
    public final boolean isCurrencyForMakerFeeBtc;
    public final boolean useSavingsWallet;
    public final BtcWalletService walletService;
    public final TradeWalletService tradeWalletService;
    public final BsqWalletService bsqWalletService;
    public final OfferBookService offerBookService;
    public final User user;
    public boolean offerAddedToOfferBook;
    private Transaction transaction;

    public PlaceOfferModel(Offer offer,
                           Coin reservedFundsForOffer,
                           Coin makerFee,
                           boolean isCurrencyForMakerFeeBtc,
                           boolean useSavingsWallet,
                           BtcWalletService walletService,
                           TradeWalletService tradeWalletService,
                           BsqWalletService bsqWalletService,
                           OfferBookService offerBookService,
                           User user) {
        this.offer = offer;
        this.reservedFundsForOffer = reservedFundsForOffer;
        this.makerFee = makerFee;
        this.isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc;
        this.useSavingsWallet = useSavingsWallet;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.bsqWalletService = bsqWalletService;
        this.offerBookService = offerBookService;
        this.user = user;
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
