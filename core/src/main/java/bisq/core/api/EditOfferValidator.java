package bisq.core.api;

import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;

import bisq.proto.grpc.EditOfferRequest;

import java.math.BigDecimal;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import static bisq.proto.grpc.EditOfferRequest.EditType.*;
import static java.lang.String.format;

@Slf4j
class EditOfferValidator {

    public final BiPredicate<Offer, EditOfferRequest.EditType> isEditingUseMktPriceMarginFlag = (offer, editType) -> {
        if (editType.equals(ACTIVATION_STATE_ONLY)) {
            // If only changing activation state, we are not editing offer.isUseMarketBasedPrice flag.
            return offer.isUseMarketBasedPrice();
        } else {
            return editType.equals(MKT_PRICE_MARGIN_ONLY)
                    || editType.equals(MKT_PRICE_MARGIN_AND_ACTIVATION_STATE)
                    || editType.equals(TRIGGER_PRICE_ONLY)
                    || editType.equals(TRIGGER_PRICE_AND_ACTIVATION_STATE)
                    || editType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE)
                    || editType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE_AND_ACTIVATION_STATE);
        }
    };

    public final Predicate<EditOfferRequest.EditType> isEditingMktPriceMargin = (editType) ->
            editType.equals(MKT_PRICE_MARGIN_ONLY)
                    || editType.equals(MKT_PRICE_MARGIN_AND_ACTIVATION_STATE)
                    || editType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE)
                    || editType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE_AND_ACTIVATION_STATE);

    public final Predicate<EditOfferRequest.EditType> isEditingTriggerPrice = (editType) ->
            editType.equals(TRIGGER_PRICE_ONLY)
                    || editType.equals(TRIGGER_PRICE_AND_ACTIVATION_STATE)
                    || editType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE)
                    || editType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE_AND_ACTIVATION_STATE);

    public final Predicate<EditOfferRequest.EditType> isEditingFixedPrice = (editType) ->
            editType.equals(FIXED_PRICE_ONLY) || editType.equals(FIXED_PRICE_AND_ACTIVATION_STATE);


    private final OpenOffer currentlyOpenOffer;
    private final String newPriceAsString;
    private final boolean newIsUseMarketBasedPrice;
    private final double newMarketPriceMargin;
    private final long newTriggerPrice;
    private final int newEnable;
    private final EditOfferRequest.EditType editType;

    private final boolean isZeroEditedFixedPriceString;
    private final boolean isZeroEditedTriggerPrice;

    EditOfferValidator(OpenOffer currentlyOpenOffer,
                       String newPriceAsString,
                       boolean newIsUseMarketBasedPrice,
                       double newMarketPriceMargin,
                       long newTriggerPrice,
                       int newEnable,
                       EditOfferRequest.EditType editType) {
        this.currentlyOpenOffer = currentlyOpenOffer;
        this.newPriceAsString = newPriceAsString;
        // The client cannot determine what offer.isUseMarketBasedPrice should be
        // when editType = ACTIVATION_STATE_ONLY.  Override newIsUseMarketBasedPrice
        // param for the ACTIVATION_STATE_ONLY case.
        // A cleaner solution might be possible if the client fetched the offer
        // before sending an edit request, but that's an extra round trip to the server.
        this.newIsUseMarketBasedPrice = editType.equals(ACTIVATION_STATE_ONLY)
                ? currentlyOpenOffer.getOffer().isUseMarketBasedPrice()
                : newIsUseMarketBasedPrice;
        this.newMarketPriceMargin = newMarketPriceMargin;
        this.newTriggerPrice = newTriggerPrice;
        this.newEnable = newEnable;
        this.editType = editType;

        this.isZeroEditedFixedPriceString = new BigDecimal(newPriceAsString).doubleValue() == 0;
        this.isZeroEditedTriggerPrice = newTriggerPrice == 0;
    }

    EditOfferValidator validate() {
        log.info("Verifying 'editoffer' params for editType {}", editType);
        checkNotBsqSwapOffer();
        switch (editType) {
            case ACTIVATION_STATE_ONLY: {
                validateEditedActivationState();
                break;
            }
            case FIXED_PRICE_ONLY:
            case FIXED_PRICE_AND_ACTIVATION_STATE: {
                validateEditedFixedPrice();
                break;
            }
            case MKT_PRICE_MARGIN_ONLY:
            case MKT_PRICE_MARGIN_AND_ACTIVATION_STATE:
            case TRIGGER_PRICE_ONLY:
            case TRIGGER_PRICE_AND_ACTIVATION_STATE:
            case MKT_PRICE_MARGIN_AND_TRIGGER_PRICE:
            case MKT_PRICE_MARGIN_AND_TRIGGER_PRICE_AND_ACTIVATION_STATE: {
                checkNotBsqOffer();
                validateEditedTriggerPrice();
                validateEditedMarketPriceMargin();
                break;
            }
            default:
                break;
        }
        return this;
    }

    @Override
    public String toString() {
        boolean isEditingMktPriceMargin = this.isEditingMktPriceMargin.test(editType);
        boolean isEditingPrice = isEditingFixedPrice.test(editType);
        var offer = currentlyOpenOffer.getOffer();
        return "EditOfferValidator{" + "\n" +
                "  offer=" + offer.getId() + "\n" +
                ", offer.payloadBase.price=" + offer.getOfferPayloadBase().getPrice() + "\n" +
                ", newPriceAsString=" + (isEditingPrice ? newPriceAsString : "N/A") + "\n" +
                ", offer.useMarketBasedPrice=" + offer.isUseMarketBasedPrice() + "\n" +
                ", newUseMarketBasedPrice=" + newIsUseMarketBasedPrice + "\n" +
                ", offer.marketPriceMargin=" + offer.getMarketPriceMargin() + "\n" +
                ", newMarketPriceMargin=" + (isEditingMktPriceMargin ? newMarketPriceMargin : "N/A") + "\n" +
                ", offer.triggerPrice=" + currentlyOpenOffer.getTriggerPrice() + "\n" +
                ", newTriggerPrice=" + (isEditingTriggerPrice.test(editType) ? newTriggerPrice : "N/A") + "\n" +
                ", newEnable=" + newEnable + "\n" +
                ", editType=" + editType + "\n" +
                '}';
    }

    private void validateEditedActivationState() {
        if (newEnable < 0)
            throw new IllegalStateException(
                    format("programmer error: the 'enable' request parameter does not"
                                    + " indicate activation state of offer with id '%s' should be changed.",
                            currentlyOpenOffer.getId()));

        var enableDescription = newEnable == 0 ? "deactivate" : "activate";
        var pricingDescription = currentlyOpenOffer.getOffer().isUseMarketBasedPrice()
                ? "mkt price margin"
                : "fixed price";
        log.info("Attempting to {} {} offer with id '{}'.",
                enableDescription,
                pricingDescription,
                currentlyOpenOffer.getId());
    }

    private void validateEditedFixedPrice() {
        if (currentlyOpenOffer.getOffer().isUseMarketBasedPrice())
            log.info("Attempting to change mkt price margin based offer with id '{}' to fixed price offer.",
                    currentlyOpenOffer.getId());

        if (newIsUseMarketBasedPrice)
            throw new IllegalStateException(
                    format("programmer error: cannot change fixed price (%s)"
                                    + " in mkt price based offer with id '%s'",
                            newMarketPriceMargin,
                            currentlyOpenOffer.getId()));

        if (!isZeroEditedTriggerPrice)
            throw new IllegalStateException(
                    format("programmer error: cannot change trigger price (%s)"
                                    + " in offer with id '%s' when changing fixed price",
                            newTriggerPrice,
                            currentlyOpenOffer.getId()));

    }

    private void validateEditedMarketPriceMargin() {
        if (!currentlyOpenOffer.getOffer().isUseMarketBasedPrice())
            log.info("Attempting to change fixed price offer with id '{}' to mkt price margin based offer.",
                    currentlyOpenOffer.getId());

        if (!isZeroEditedFixedPriceString)
            throw new IllegalStateException(
                    format("programmer error: cannot set fixed price (%s)"
                                    + " in mkt price margin based offer with id '%s'",
                            newPriceAsString,
                            currentlyOpenOffer.getId()));
    }

    private void validateEditedTriggerPrice() {
        if (!currentlyOpenOffer.getOffer().isUseMarketBasedPrice()
                && !newIsUseMarketBasedPrice
                && !isZeroEditedTriggerPrice)
            throw new IllegalStateException(
                    format("programmer error: cannot set a trigger price"
                                    + " in fixed price offer with id '%s'",
                            currentlyOpenOffer.getId()));

        if (newTriggerPrice < 0)
            throw new IllegalStateException(
                    format("programmer error: cannot set trigger price to a negative value"
                                    + " in offer with id '%s'",
                            currentlyOpenOffer.getId()));
    }

    private void checkNotBsqOffer() {
        if ("BSQ".equals(currentlyOpenOffer.getOffer().getCurrencyCode())) {
            throw new IllegalStateException(
                    format("cannot set mkt price margin or trigger price on fixed price bsq offer with id '%s'",
                            currentlyOpenOffer.getId()));
        }
    }

    private void checkNotBsqSwapOffer() {
        if (currentlyOpenOffer.getOffer().isBsqSwapOffer()) {
            throw new IllegalStateException(
                    format("cannot edit bsq swap offer with id '%s'", currentlyOpenOffer.getId()));
        }
    }
}
