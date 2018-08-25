package bisq.httpapi.model;

import java.util.List;

import lombok.Value;

@Value
public class OfferList {

    private List<OfferDetail> offers;

    public OfferList(List<OfferDetail> offers) {
        this.offers = offers;
    }

    public long getTotal() {
        return offers.size();
    }
}
