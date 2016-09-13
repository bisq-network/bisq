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

public final class FasterPaymentsAccountContractData extends PaymentAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String sortCode;
    private String accountNr;

    public FasterPaymentsAccountContractData(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public void setAccountNr(String accountNr) {
        this.accountNr = accountNr;
    }

    public String getAccountNr() {
        return accountNr;
    }

    public String getSortCode() {
        return sortCode;
    }

    public void setSortCode(String sortCode) {
        this.sortCode = sortCode;
    }

    @Override
    public String getPaymentDetails() {
        return "FasterPayments - UK Sort code: " + sortCode + ", Account number: " + accountNr;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "UK Sort code: " + sortCode + "\n" +
                "Account number: " + accountNr;
    }
}
