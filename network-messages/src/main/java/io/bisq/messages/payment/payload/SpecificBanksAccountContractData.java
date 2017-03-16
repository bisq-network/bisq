/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.messages.payment.payload;

import com.google.common.base.Joiner;
import io.bisq.app.Version;
import io.bisq.common.wire.proto.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Locale;

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
        return null;
    }

    @Override
    public String getPaymentDetailsForTradePopup(Locale locale) {
        return super.getPaymentDetailsForTradePopup(locale) + "\n" +
                "Accepted banks: " + Joiner.on(", ").join(acceptedBanks);
    }

    @Override
    public Messages.PaymentAccountContractData toProtoBuf() {
        Messages.SpecificBanksAccountContractData.Builder specificBanksAccountContractData =
                Messages.SpecificBanksAccountContractData.newBuilder().addAllAcceptedBanks(acceptedBanks);
        Messages.BankAccountContractData.Builder bankAccountContractData =
                Messages.BankAccountContractData.newBuilder()
                        .setHolderName(holderName)
                        .setBankName(bankName)
                        .setBankId(bankId)
                        .setBranchId(branchId)
                        .setAccountNr(accountNr)
                        .setAccountType(accountType)
                        .setHolderTaxId(holderTaxId)
                        .setSpecificBanksAccountContractData(specificBanksAccountContractData);
        Messages.CountryBasedPaymentAccountContractData.Builder countryBasedPaymentAccountContractData =
                Messages.CountryBasedPaymentAccountContractData.newBuilder()
                        .setCountryCode(countryCode)
                        .setBankAccountContractData(bankAccountContractData);
        Messages.PaymentAccountContractData.Builder paymentAccountContractData =
                Messages.PaymentAccountContractData.newBuilder()
                        .setId(id)
                        .setPaymentMethodName(paymentMethodName)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setCountryBasedPaymentAccountContractData(countryBasedPaymentAccountContractData);

        return paymentAccountContractData.build();
    }
}
