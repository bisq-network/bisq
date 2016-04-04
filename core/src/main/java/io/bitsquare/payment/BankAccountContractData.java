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
import io.bitsquare.locale.BankUtil;
import io.bitsquare.locale.CountryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public abstract class BankAccountContractData extends CountryBasedPaymentAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(BankAccountContractData.class);

    protected String holderName;
    protected String bankName;
    protected String bankId;
    protected String branchId = "-";
    protected String accountNr;

    @Nullable
    protected String holderTaxId;

    public BankAccountContractData(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    @Override
    public String getPaymentDetails() {
        return "National Bank transfer - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        String holderIdString = BankUtil.requiresHolderId(countryCode) ? (getHolderIdLabel() + ": " + holderTaxId + "\n") : "";
        return "Holder name: " + holderName + "\n" +
                "Bank name: " + bankName + "\n" +
                "Bank Nr.: " + bankId + "\n" +
                "Branch Nr.: " + branchId + "\n" +
                "Account Nr.: " + accountNr + "\n" +
                holderIdString +
                "Country of bank: " + CountryUtil.getNameAndCode(getCountryCode());
    }


    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public String getBankId() {
        return bankId;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getAccountNr() {
        return accountNr;
    }

    public void setAccountNr(String accountNr) {
        this.accountNr = accountNr;
    }

    @Nullable
    public String getHolderTaxId() {
        return holderTaxId;
    }

    public void setHolderTaxId(String holderTaxId) {
        this.holderTaxId = holderTaxId;
    }

    public String getHolderIdLabel() {
        return BankUtil.getHolderIdLabel(countryCode);
    }
}
