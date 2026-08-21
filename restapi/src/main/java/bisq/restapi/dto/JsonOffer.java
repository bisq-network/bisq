package bisq.restapi.dto;

import lombok.Value;

@Value
public class JsonOffer {
    String direction;
    String currencyCode;
    long minAmount;
    long amount;
    long price;
    long date;
    boolean useMarketBasedPrice;
    double marketPriceMargin;
    String paymentMethod;
    String id;
    String currencyPair;
    String primaryMarketDirection;
    String priceDisplayString;
    String primaryMarketAmountDisplayString;
    String primaryMarketMinAmountDisplayString;
    String primaryMarketVolumeDisplayString;
    String primaryMarketMinVolumeDisplayString;
    long primaryMarketPrice;
    long primaryMarketAmount;
    long primaryMarketMinAmount;
    long primaryMarketVolume;
    long primaryMarketMinVolume;
}
