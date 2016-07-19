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

import com.google.common.base.Joiner;
import io.bitsquare.app.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public final class SpecificBanksAccountContractData extends BankAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(SpecificBanksAccountContractData.class);


    // Dont use a set here as we need a deterministic ordering, otherwise the contract hash does not match
    private ArrayList<String> acceptedBanks;

    public SpecificBanksAccountContractData(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
        acceptedBanks = new ArrayList<>();
    }

    public void clearAcceptedBanks() {
        acceptedBanks = new ArrayList<>();
    }

    public void addAcceptedBank(String bankName) {
        if (!acceptedBanks.contains(bankName))
            acceptedBanks.add(bankName);
    }

    public ArrayList<String> getAcceptedBanks() {
        return acceptedBanks;
    }


    @Override
    public String getPaymentDetails() {
        return "Transfers with specific banks - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return super.getPaymentDetailsForTradePopup() + "\n" +
                "Accepted banks: " + Joiner.on(", ").join(acceptedBanks);
    }
}
