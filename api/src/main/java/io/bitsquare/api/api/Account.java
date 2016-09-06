package io.bitsquare.api.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.PaymentAccount;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * [
 * {
 * "account_id": "c4e4645a-18e6-45be-8853-c7ebac68f0a4",
 * "created": 1473010076100,
 * "payment_method": {
 * "payment_method_id": "SEPA",
 * "lock_time": 0,
 * "max_trade_period": 691200000,
 * "max_trade_limit": {
 * "value": 75000000,
 * "positive": true,
 * "zero": false,
 * "negative": false
 * }
 * },
 * "account_name": "SEPA, EUR, BE, BE...",
 * "trade_currencies": ['EUR'],
 * "selected_trade_currency": 'EUR',
 * "contract_data": {
 * "payment_method_name": "SEPA",
 * "contract_id": "c4e4645a-18e6-45be-8853-c7ebac68f0a4",
 * "max_trade_period": 691200000,
 * "country_code": "BE",
 * "holder_name": "Mike Rosseel",
 * "iban": "BE82063500018968",
 * "bic": "GKCCBEBB",
 * "accepted_country_codes": ["AT", "BE", "CY", "DE", "EE", "ES", "FI", "FR", "GR", "IE", "IT", "LT", "LU", "LV", "MC", "MT", "NL", "PT", "SI", "SK"],
 * "payment_details": "SEPA - Holder name: Mike Rosseel, IBAN: BE82063500018968, BIC: GKCCBEBB, country code: BE",
 * "payment_details_for_trade_popup": "Holder name: Mike Rosseel
 * IBAN: BE82063500018968
 * BIC: GKCCBEBB
 * Country of bank: Belgium (BE)"
 * },
 * "country": {
 * "code": "BE",
 * "name": "Belgium",
 * "region": {
 * "code": "EU",
 * "name": "Europe"
 * }
 * },
 * "accepted_country_codes": ["AT", "BE", "CY", "DE", "EE", "ES", "FI", "FR", "GR", "IE", "IT", "LT", "LU", "LV", "MC", "MT", "NL", "PT", "SI", "SK"],
 * "bank_id": "GKCCBEBB",
 * "iban": "BE82063500018968",
 * "holder_name": "Mike Rosseel",
 * "bic": "GKCCBEBB",
 * "single_trade_currency": 'EUR',
 * "payment_details": "SEPA - Holder name: Mike Rosseel, IBAN: BE82063500018968, BIC: GKCCBEBB, country code: BE"
 * },
 * ...
 * ]
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Account {
    @JsonProperty
    String account_id;
    @JsonProperty
    long created;
    @JsonProperty
    AccountPaymentMethod paymentMethod;
    @JsonProperty
    String account_name;
    @JsonProperty
    List<String> trade_currencies;
    @JsonProperty
    String selected_trade_currency;
    @JsonProperty
    ContractData contract_data;
    @JsonProperty
    Country country;
    @JsonProperty
    String bank_id;
    @JsonProperty
    String iban;
    @JsonProperty
    String holder_name;
    @JsonProperty
    String bic;
    @JsonProperty
    TradeCurrency single_trade_currency;
    @JsonProperty
    String paymentDetails;
    @JsonProperty
    List<String> accepted_country_codes;


    public Account(PaymentAccount bitsquarePaymentAccount) {
        this.account_id = bitsquarePaymentAccount.getId();
        this.created = bitsquarePaymentAccount.getCreationDate().toInstant().toEpochMilli();
        this.paymentMethod = new AccountPaymentMethod(bitsquarePaymentAccount.getPaymentMethod());
        this.account_name = bitsquarePaymentAccount.getAccountName();
        if (!CollectionUtils.isEmpty(bitsquarePaymentAccount.getTradeCurrencies())) {
            this.trade_currencies = bitsquarePaymentAccount.getTradeCurrencies().stream()
                    .map(tradeCurrency -> tradeCurrency.getCode()).collect(Collectors.toList());
        }
        if(bitsquarePaymentAccount.getSelectedTradeCurrency() != null) {
            this.selected_trade_currency = bitsquarePaymentAccount.getSelectedTradeCurrency().getCode();
        }
        this.contract_data = new ContractData(bitsquarePaymentAccount);
    }
}
