package bisq.httpapi.exceptions;

public class OfferTakerSameAsMakerException extends Exception {
    public OfferTakerSameAsMakerException(String message) {
        super(message);
    }
}
