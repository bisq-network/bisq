package bisq.httpapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;



import bisq.httpapi.model.validation.CountryCode;
import bisq.httpapi.model.validation.NotNullItems;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Preferences {

    public Boolean autoSelectArbitrators;
    public String baseCurrencyNetwork;
    public String blockChainExplorer;
    public List<String> cryptoCurrencies;
    public List<String> fiatCurrencies;
    @NotNullItems
    public List<String> ignoredTraders;
    public Double maxPriceDistance;
    public String preferredTradeCurrency;
    public Boolean useCustomWithdrawalTxFee;
    @CountryCode
    public String userCountry;
    public String userLanguage;
    public Long withdrawalTxFee;

}
