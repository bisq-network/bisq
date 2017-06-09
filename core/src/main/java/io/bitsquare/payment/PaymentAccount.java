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

package io.bitsquare.payment;

import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import io.bitsquare.locale.TradeCurrency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public abstract class PaymentAccount implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(PaymentAccount.class);

    protected final String id;
    protected final Date creationDate;
    protected final PaymentMethod paymentMethod;
    protected String accountName;
    final List<TradeCurrency> tradeCurrencies = new ArrayList<>();
    protected TradeCurrency selectedTradeCurrency;
    public final PaymentAccountContractData contractData;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    protected PaymentAccount(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        id = UUID.randomUUID().toString();
        creationDate = new Date();
        contractData = setContractData();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addCurrency(TradeCurrency tradeCurrency) {
        if (!tradeCurrencies.contains(tradeCurrency))
            tradeCurrencies.add(tradeCurrency);
    }

    public void removeCurrency(TradeCurrency tradeCurrency) {
        if (tradeCurrencies.contains(tradeCurrency))
            tradeCurrencies.remove(tradeCurrency);
    }

    public boolean hasMultipleCurrencies() {
        return tradeCurrencies.size() > 1;
    }

    public void setSingleTradeCurrency(TradeCurrency tradeCurrency) {
        tradeCurrencies.clear();
        tradeCurrencies.add(tradeCurrency);
        setSelectedTradeCurrency(tradeCurrency);
    }

    public TradeCurrency getSingleTradeCurrency() {
        if (!tradeCurrencies.isEmpty())
            return tradeCurrencies.get(0);
        else
            return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter, Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract PaymentAccountContractData setContractData();

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setSelectedTradeCurrency(TradeCurrency tradeCurrency) {
        selectedTradeCurrency = tradeCurrency;
    }

    public TradeCurrency getSelectedTradeCurrency() {
        return selectedTradeCurrency;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return id;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public List<TradeCurrency> getTradeCurrencies() {
        return tradeCurrencies;
    }

    public PaymentAccountContractData getContractData() {
        return contractData;
    }

    public String getPaymentDetails() {
        return contractData.getPaymentDetails();
    }

    public Date getCreationDate() {
        return creationDate;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;

        PaymentAccount that = (PaymentAccount) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (creationDate != null ? !creationDate.equals(that.creationDate) : that.creationDate != null) return false;
        if (paymentMethod != null ? !paymentMethod.equals(that.paymentMethod) : that.paymentMethod != null)
            return false;
        if (accountName != null ? !accountName.equals(that.accountName) : that.accountName != null) return false;
        if (tradeCurrencies != null ? !tradeCurrencies.equals(that.tradeCurrencies) : that.tradeCurrencies != null)
            return false;
        if (selectedTradeCurrency != null ? !selectedTradeCurrency.equals(that.selectedTradeCurrency) : that.selectedTradeCurrency != null)
            return false;
        return !(contractData != null ? !contractData.equals(that.contractData) : that.contractData != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (creationDate != null ? creationDate.hashCode() : 0);
        result = 31 * result + (paymentMethod != null ? paymentMethod.hashCode() : 0);
        result = 31 * result + (accountName != null ? accountName.hashCode() : 0);
        result = 31 * result + (tradeCurrencies != null ? tradeCurrencies.hashCode() : 0);
        result = 31 * result + (selectedTradeCurrency != null ? selectedTradeCurrency.hashCode() : 0);
        result = 31 * result + (contractData != null ? contractData.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PaymentAccount{" +
                "id='" + id + '\'' +
                ", creationDate=" + creationDate +
                ", paymentMethod=" + paymentMethod +
                ", accountName='" + accountName + '\'' +
                ", tradeCurrencies=" + tradeCurrencies +
                ", selectedTradeCurrency=" + selectedTradeCurrency +
                ", contractData=" + contractData +
                '}';
    }
}
