package io.bitsquare.trade.protocol.createoffer;

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.handlers.FaultHandler;
import io.bitsquare.trade.handlers.PublishTransactionResultHandler;
import io.bitsquare.trade.protocol.createoffer.tasks.PayOfferFee;
import io.bitsquare.trade.protocol.createoffer.tasks.PublishOfferToDHT;
import io.bitsquare.trade.protocol.createoffer.tasks.ValidateOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for coordinating tasks involved in the create offer process.
 * It holds the state of the current process and support recovery if possible.
 */
//TODO recover policy, timer
public class CreateOfferCoordinator
{
    public enum State
    {
        INIT,
        OFFER_FEE_PAID,
        OFFER_PUBLISHED_TO_DHT
    }

    private static final Logger log = LoggerFactory.getLogger(CreateOfferCoordinator.class);

    private final Offer offer;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private PublishTransactionResultHandler resultHandler;
    private FaultHandler faultHandler;

    private State state;

    // result
    private String transactionId;

    public CreateOfferCoordinator(Offer offer, WalletFacade walletFacade, MessageFacade messageFacade)
    {
        this.offer = offer;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
    }

    public void start(PublishTransactionResultHandler resultHandler, FaultHandler faultHandler)
    {
        this.resultHandler = resultHandler;
        this.faultHandler = faultHandler;

        state = State.INIT;
        ValidateOffer.run(this::onOfferValidated, this::onFailed, offer);
    }

    private void onOfferValidated()
    {
        PayOfferFee.run(this::onOfferFeePaid, this::onFailed, walletFacade, offer);
    }

    private void onOfferFeePaid(String transactionId)
    {
        state = State.OFFER_FEE_PAID;

        this.transactionId = transactionId;
        PublishOfferToDHT.run(this::onOfferPublishedToDHT, this::onFailed, messageFacade, offer);
    }

    private void onOfferPublishedToDHT()
    {
        state = State.OFFER_PUBLISHED_TO_DHT;

        resultHandler.onResult(transactionId);
    }

    private void onFailed(String message, Throwable throwable)
    {
        //TODO recover policy, timer

        faultHandler.onFault(message, throwable);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Recovery 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void recover(State lastState, String transactionId, PublishTransactionResultHandler resultHandler, FaultHandler faultHandler)
    {
        this.transactionId = transactionId;
        this.resultHandler = resultHandler;
        this.faultHandler = faultHandler;
        switch (lastState)
        {
            case INIT:
                PayOfferFee.run(this::onOfferFeePaid, this::onFailed, walletFacade, offer);
                break;
            case OFFER_FEE_PAID:
                PublishOfferToDHT.run(this::onOfferPublishedToDHT, this::onFailed, messageFacade, offer);
                break;
            case OFFER_PUBLISHED_TO_DHT:
                // should be impossible
                resultHandler.onResult(transactionId);
                break;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters for persisting state 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getTransactionId()
    {
        return transactionId;
    }

    public State getState()
    {
        return state;
    }
}
