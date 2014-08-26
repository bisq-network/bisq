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

import io.bitsquare.bank.BankAccount;
import io.bitsquare.util.DSAKeyUtil;

import com.google.bitcoin.core.Coin;

import java.io.Serializable;

import java.security.PublicKey;

public class Contract implements Serializable {
    private static final long serialVersionUID = 71472356206100158L;

    private final Offer offer;
    private final String takeOfferFeeTxID;
    private final Coin tradeAmount;
    private final String offererAccountID;
    private final String takerAccountID;
    private final BankAccount offererBankAccount;
    private final BankAccount takerBankAccount;
    private final String offererMessagePublicKeyAsString;
    private final String takerMessagePublicKeyAsString;

    public Contract(Offer offer,
                    Coin tradeAmount,
                    String takeOfferFeeTxID,
                    String offererAccountID,
                    String takerAccountID,
                    BankAccount offererBankAccount,
                    BankAccount takerBankAccount,
                    PublicKey offererMessagePublicKey,
                    PublicKey takerMessagePublicKey) {
        this.offer = offer;
        this.tradeAmount = tradeAmount;
        this.takeOfferFeeTxID = takeOfferFeeTxID;
        this.offererAccountID = offererAccountID;
        this.takerAccountID = takerAccountID;
        this.offererBankAccount = offererBankAccount;
        this.takerBankAccount = takerBankAccount;
        this.offererMessagePublicKeyAsString = DSAKeyUtil.getHexStringFromPublicKey(offererMessagePublicKey);
        this.takerMessagePublicKeyAsString = DSAKeyUtil.getHexStringFromPublicKey(takerMessagePublicKey);
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

    public BankAccount getOffererBankAccount() {
        return offererBankAccount;
    }

    public BankAccount getTakerBankAccount() {
        return takerBankAccount;
    }

    public String getTakerMessagePublicKey() {
        return takerMessagePublicKeyAsString;
    }

    public String getOffererMessagePublicKey() {
        return offererMessagePublicKeyAsString;
    }

    @Override
    public String toString() {
        return "Contract{" +
                "offer=" + offer +
                ", takeOfferFeeTxID='" + takeOfferFeeTxID + '\'' +
                ", tradeAmount=" + tradeAmount +
                ", offererAccountID='" + offererAccountID + '\'' +
                ", takerAccountID='" + takerAccountID + '\'' +
                ", offererBankAccount=" + offererBankAccount +
                ", takerBankAccount=" + takerBankAccount +
                ", takerMessagePublicKeyAsString=" + takerMessagePublicKeyAsString +
                ", offererMessagePublicKeyAsString=" + offererMessagePublicKeyAsString +
                '}';
    }
}
