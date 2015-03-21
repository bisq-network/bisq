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

package io.bitsquare.trade;

import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.offer.Offer;
import io.bitsquare.util.DSAKeyUtil;

import org.bitcoinj.core.Coin;

import java.io.Serializable;

import java.security.PublicKey;

//TODO flatten down?
// TODO The relation Offer, Trade and Contract need to be reviewed and might be changed

public class Contract implements Serializable {
    private static final long serialVersionUID = 71472356206100158L;

    private final Offer offer;
    private final String takeOfferFeeTxID;
    private final Coin tradeAmount;
    private final String offererAccountID;
    private final String takerAccountID;
    private final FiatAccount offererFiatAccount;
    private final FiatAccount takerFiatAccount;
    private final String offererP2PSigPubKeyAsString;
    private final String takerP2PSigPubKeyAsString;

    public Contract(Offer offer,
                    Coin tradeAmount,
                    String takeOfferFeeTxID,
                    String offererAccountID,
                    String takerAccountID,
                    FiatAccount offererFiatAccount,
                    FiatAccount takerFiatAccount,
                    PublicKey offererP2PSigPubKey,
                    PublicKey takerP2PSigPubKey) {
        this.offer = offer;
        this.tradeAmount = tradeAmount;
        this.takeOfferFeeTxID = takeOfferFeeTxID;
        this.offererAccountID = offererAccountID;
        this.takerAccountID = takerAccountID;
        this.offererFiatAccount = offererFiatAccount;
        this.takerFiatAccount = takerFiatAccount;
        this.offererP2PSigPubKeyAsString = DSAKeyUtil.getHexStringFromPublicKey(offererP2PSigPubKey);
        this.takerP2PSigPubKeyAsString = DSAKeyUtil.getHexStringFromPublicKey(takerP2PSigPubKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer getOffer() {
        return offer;
    }

    public String getTakeOfferFeeTxID() {
        return takeOfferFeeTxID;
    }

    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public String getOffererAccountID() {
        return offererAccountID;
    }

    public String getTakerAccountID() {
        return takerAccountID;
    }

    public FiatAccount getOffererFiatAccount() {
        return offererFiatAccount;
    }

    public FiatAccount getTakerFiatAccount() {
        return takerFiatAccount;
    }

    public String getTakerMessagePublicKey() {
        return takerP2PSigPubKeyAsString;
    }

    public String getOffererMessagePublicKey() {
        return offererP2PSigPubKeyAsString;
    }

    @Override
    public String toString() {
        return "Contract{" +
                "offer=" + offer +
                ", takeOfferFeeTxID='" + takeOfferFeeTxID + '\'' +
                ", tradeAmount=" + tradeAmount +
                ", offererAccountID='" + offererAccountID + '\'' +
                ", takerAccountID='" + takerAccountID + '\'' +
                ", offererBankAccount=" + offererFiatAccount +
                ", takerBankAccount=" + takerFiatAccount +
                ", takerP2PSigPubKeyAsString=" + takerP2PSigPubKeyAsString +
                ", offererP2PSigPubKeyAsString=" + offererP2PSigPubKeyAsString +
                '}';
    }
}
