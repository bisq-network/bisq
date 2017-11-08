package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import lombok.Data;

/**
 * "contractData": {
 * "paymentMethodName": "SEPA",
 * "id": "c4e4645a-18e6-45be-8853-c7ebac68f0a4",
 * "maxTradePeriod": 691200000,
 * "countryCode": "BE",
 * "holderName": "Mike Rosseel",
 * "iban": "BE82063500018968",
 * "bic": "GKCCBEBB",
 * "acceptedCountryCodes": ["AT", "BE", "CY", "DE", "EE", "ES", "FI", "FR", "GR", "IE", "IT", "LT", "LU", "LV", "MC", "MT", "NL", "PT", "SI", "SK"],
 * "paymentDetails": "SEPA - Holder name: Mike Rosseel, IBAN: BE82063500018968, BIC: GKCCBEBB, country code: BE",
 * "paymentDetailsForTradePopup": "Holder name: Mike Rosseel\nIBAN: BE82063500018968\nBIC: GKCCBEBB\nCountry of bank: Belgium (BE)"
 * },
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
public class ContractData {
    PaymentAccountPayload contractData;
    /*
    @JsonProperty
    String payment_method_id;
    @JsonProperty
    String contract_id;
    @JsonProperty
    long max_trade_period;
    @JsonProperty
    String country_code;
    @JsonProperty
    String holder_name;
    @JsonProperty
    String iban;
    @JsonProperty
    String bic;
    @JsonProperty
    List<String> accepted_country_codes;
    @JsonProperty
    String payment_details;
    @JsonProperty
    String bank_id;
    @JsonProperty
    String single_trade_currency;
*/

    public ContractData(PaymentAccount bitsquarePaymentAccount) {
        this.contractData = bitsquarePaymentAccount.getPaymentAccountPayload();
        /*
        this.payment_method_id = contractData.getPaymentMethodId();
        this.contract_id = contractData.getId();
        this.max_trade_period = contractData.getMaxTradePeriod();

        if (bitsquarePaymentAccount instanceof CountryBasedPaymentAccount) {
            this.country_code = ((CountryBasedPaymentAccount) bitsquarePaymentAccount).getCountry().code;
        }
        if (bitsquarePaymentAccount instanceof SepaAccount) {
            iban = ((SepaAccount) bitsquarePaymentAccount).getIban();
            holder_name = ((SepaAccount) bitsquarePaymentAccount).getHolderName();
            bic = ((SepaAccount) bitsquarePaymentAccount).getBic();
            accepted_country_codes = ((SepaAccount) bitsquarePaymentAccount).getAcceptedCountryCodes();
        } else if (bitsquarePaymentAccount instanceof BankAccount) {
            bank_id = ((BankAccount) bitsquarePaymentAccount).getBankId();
        }

        single_trade_currency = bitsquarePaymentAccount.getSingleTradeCurrency().getCode();
        payment_details = bitsquarePaymentAccount.getPaymentAccountPayload().getPaymentDetailsForTradePopup();
*/
    }
}
