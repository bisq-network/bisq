package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
public class OfferDetail {
    @JsonProperty
    String offer_id;
    @JsonProperty
    OfferPayload.Direction direction;
    @JsonProperty
    Offer.State state;
    @JsonProperty
    Date created;
    @JsonProperty
    List<String> arbitrators;
    @JsonProperty
    String offerer;
    @JsonProperty
    String btc_amount;
    @JsonProperty
    String min_btc_amount;
    @JsonProperty
    String other_amount;
    @JsonProperty
    String other_currency;
    @JsonProperty
    PriceDetail price_detail;
    // paymentmethod - important for automating
    // offerfeepaymenttxid ???


    public OfferDetail(Offer offer) {
        this.offer_id = offer.getId();
        this.direction = offer.getDirection();
        this.state = offer.getState();
        this.created = offer.getDate();
        this.arbitrators = offer.getArbitratorNodeAddresses().stream()
                .map(nodeAddress -> nodeAddress.toString()).collect(Collectors.toList());
        this.offerer = offer.getMakerNodeAddress().toString();
        this.btc_amount = offer.getAmount().toPlainString();
        this.min_btc_amount = offer.getMinAmount().toPlainString();
        if (offer.getPrice() != null) {
            this.other_amount = offer.getPrice().toPlainString();
            this.other_currency = offer.getPrice().getCurrencyCode();
        }

        this.price_detail = new PriceDetail(offer.isUseMarketBasedPrice(),
                offer.getMarketPriceMargin());
    }

    @Data
    @AllArgsConstructor
    class PriceDetail {
        @JsonProperty
        boolean use_market_price;
        @JsonProperty
        double market_price_margin;
    }
}

