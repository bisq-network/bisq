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

package io.bitsquare.payment.unused;

import io.bitsquare.app.Version;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

// US only
public class FedWireAccount extends PaymentAccount implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(FedWireAccount.class);

    private String holderName;
    private String holderState;
    private String holderZIP;
    private String holderStreet;
    private String holderCity;
    private String holderSSN; // Optional? social security Nr only Arizona and Oklahoma?
    private String accountNr;
    private String bankCode;// SWIFT Code/BIC/RoutingNr/ABA (ABA for UD domestic)
    private String bankName;
    private String bankState;
    private String bankZIP;
    private String bankStreet;
    private String bankCity;

    private FedWireAccount() {
        super(PaymentMethod.SEPA);
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getAccountNr() {
        return accountNr;
    }

    public void setAccountNr(String accountNr) {
        this.accountNr = accountNr;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    @Override
    public String getPaymentDetails() {
        return "{accountName='" + accountName + '\'' +
                '}';
    }

    @Override
    public String toString() {
        return "SepaAccount{" +
                "accountName='" + accountName + '\'' +
                ", id='" + id + '\'' +
                ", paymentMethod=" + paymentMethod +
                ", holderName='" + holderName + '\'' +
                ", accountNr='" + accountNr + '\'' +
                ", bankCode='" + bankCode + '\'' +
                ", country=" + country +
                ", tradeCurrencies='" + getTradeCurrencies() + '\'' +
                ", selectedTradeCurrency=" + selectedTradeCurrency +
                '}';
    }
}
