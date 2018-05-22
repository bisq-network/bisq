package network.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import network.bisq.api.model.validation.CountryCode;
import network.bisq.api.model.validation.NotNullItems;

import java.util.List;

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
