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
import io.bitsquare.locale.Country;
import io.bitsquare.locale.TradeCurrency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaymentAccount implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(PaymentAccount.class);

    protected final String id;
    protected final PaymentMethod paymentMethod;
    protected String accountName;
    protected final List<TradeCurrency> tradeCurrencies = new ArrayList<>();
    protected TradeCurrency selectedTradeCurrency;
    @Nullable
    protected Country country = null;
    protected PaymentAccountContractData contractData;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    public PaymentAccount(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        id = UUID.randomUUID().toString();
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

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    @Nullable
    public Country getCountry() {
        return country;
    }

    public void setCountry(@Nullable Country country) {
        this.country = country;
        contractData.setCountryCode(country.code);
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

    public int getMaxTradePeriod() {
        return contractData.getMaxTradePeriod();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Util
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return contractData.toString() + '\'' +
                "PaymentAccount{" +
                "id='" + id + '\'' +
                ", paymentMethod=" + paymentMethod +
                ", accountName='" + accountName + '\'' +
                ", tradeCurrencies=" + tradeCurrencies +
                ", selectedTradeCurrency=" + selectedTradeCurrency +
                ", country=" + country +
                ", contractData=" + contractData +
                '}';
    }
}
