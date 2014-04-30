package io.bitsquare.trade;

import java.util.UUID;

public class Trade
{
    private Offer offer;
    private boolean takeOfferRequested;
    private boolean takeOfferAccepted;
    private double requestedAmount;
    private boolean takeOfferFeePayed;
    private boolean takeOfferFeePaymentConfirmed;
    private String jsonRepresentation;
    private String signature;
    private String takeOfferFeeTxID;

    public Trade(Offer offer)
    {
        this.offer = offer;
    }

    public Offer getOffer()
    {
        return offer;
    }

    public UUID getUid()
    {
        return offer.getUid();
    }

    public void setJsonRepresentation(String jsonRepresentation)
    {
        this.jsonRepresentation = jsonRepresentation;
    }

    public void setSignature(String signature)
    {
        this.signature = signature;
    }

    public boolean isTakeOfferRequested()
    {
        return takeOfferRequested;
    }

    public void setTakeOfferRequested(boolean takeOfferRequested)
    {
        this.takeOfferRequested = takeOfferRequested;
    }

    public boolean isTakeOfferAccepted()
    {
        return takeOfferAccepted;
    }

    public void setTakeOfferAccepted(boolean takeOfferAccepted)
    {
        this.takeOfferAccepted = takeOfferAccepted;
    }

    public double getRequestedAmount()
    {
        return requestedAmount;
    }

    public void setTradeAmount(double requestedAmount)
    {
        this.requestedAmount = requestedAmount;
    }

    public void setTakeOfferFeePayed(boolean takeOfferFeePayed)
    {
        this.takeOfferFeePayed = takeOfferFeePayed;
    }

    public void setTakeOfferFeePaymentConfirmed(boolean takeOfferFeePaymentConfirmed)
    {
        this.takeOfferFeePaymentConfirmed = takeOfferFeePaymentConfirmed;
    }

    public void setTakeOfferFeeTxID(String takeOfferFeeTxID)
    {
        this.takeOfferFeeTxID = takeOfferFeeTxID;
    }
}
