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

public final class ClearXchangeAccountContractData extends PaymentAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String holderName;
    private String emailOrMobileNr;

    public ClearXchangeAccountContractData(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public void setEmailOrMobileNr(String emailOrMobileNr) {
        this.emailOrMobileNr = emailOrMobileNr;
    }

    public String getEmailOrMobileNr() {
        return emailOrMobileNr;
    }

    @Override
    public String getPaymentDetails() {
        return "ClearXchange - Holder name: " + holderName + ", email or mobile nr.: " + emailOrMobileNr;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "Holder name: " + holderName + "\n" +
                "Email or mobile nr.: " + emailOrMobileNr;
    }
}
