package network.bisq.httpapi;

public class OfferTakerSameAsMakerException extends Exception {
    public OfferTakerSameAsMakerException(String message) {
        super(message);
    }
}
