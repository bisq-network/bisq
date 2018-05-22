package network.bisq.api.model;

import bisq.core.offer.OfferPayload;

public class ClosedTradableDetails {

    public Long amount;
    public String currencyCode;
    public Long date;
    public OfferPayload.Direction direction;
    public String id;
    public Long price;
    public String status;
    public Long volume;
}
