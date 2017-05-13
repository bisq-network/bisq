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

package io.bisq.core.proto;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.core.filter.PaymentAccountFilter;
import io.bisq.core.payment.payload.BankAccountPayload;
import io.bisq.core.payment.payload.CountryBasedPaymentAccountPayload;
import io.bisq.generated.protobuffer.PB;

public class ProtoUtil {

    public static PaymentAccountFilter getPaymentAccountFilter(PB.PaymentAccountFilter accountFilter) {
        return new PaymentAccountFilter(accountFilter.getPaymentMethodId(), accountFilter.getGetMethodName(),
                accountFilter.getValue());
    }

    public static String getCurrencyCode(PB.OfferPayload pbOffer) {
        return CurrencyUtil.isCryptoCurrency(pbOffer.getBaseCurrencyCode()) ? pbOffer.getBaseCurrencyCode() : pbOffer.getCounterCurrencyCode();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PaymentAccountPayload Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void fillInBankAccountPayload(PB.PaymentAccountPayload protoEntry, BankAccountPayload bankAccountPayload) {
        PB.BankAccountPayload bankProto = protoEntry.getCountryBasedPaymentAccountPayload().getBankAccountPayload();
        bankAccountPayload.setHolderName(bankProto.getHolderName());
        bankAccountPayload.setBankName(bankProto.getBankName());
        bankAccountPayload.setBankId(bankProto.getBankId());
        bankAccountPayload.setBranchId(bankProto.getBranchId());
        bankAccountPayload.setAccountNr(bankProto.getAccountNr());
        bankAccountPayload.setAccountType(bankProto.getAccountType());
    }

    public static void fillInCountryBasedPaymentAccountPayload(PB.PaymentAccountPayload protoEntry,
                                                               CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload) {
        countryBasedPaymentAccountPayload.setCountryCode(protoEntry.getCountryBasedPaymentAccountPayload().getCountryCode());
    }
}
