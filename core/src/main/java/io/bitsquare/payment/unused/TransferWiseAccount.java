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

public class TransferWiseAccount extends PaymentAccount implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(TransferWiseAccount.class);

    private String holderName;
    private String iban;
    private String bic;


    private TransferWiseAccount() {
        super(PaymentMethod.SEPA);
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getBic() {
        return bic;
    }

    public void setBic(String bic) {
        this.bic = bic;
    }

    @Override
    public String getPaymentDetails() {
        return "TransferWise{accountName='" + accountName + '\'' +
                '}';
    }

    @Override
    public String toString() {
        return "TransferWiseAccount{" +
                "accountName='" + accountName + '\'' +
                ", id='" + id + '\'' +
                ", paymentMethod=" + paymentMethod +
                ", holderName='" + holderName + '\'' +
                ", iban='" + iban + '\'' +
                ", bic='" + bic + '\'' +
                ", country=" + country +
                ", tradeCurrencies='" + getTradeCurrencies() + '\'' +
                ", selectedTradeCurrency=" + selectedTradeCurrency +
                '}';
    }
}
