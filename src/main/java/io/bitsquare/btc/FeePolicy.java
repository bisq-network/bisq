package io.bitsquare.btc;

import com.google.bitcoin.core.*;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class FeePolicy
{
    private static final Logger log = LoggerFactory.getLogger(FeePolicy.class);

    public static BigInteger TX_FEE = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
    public static BigInteger ACCOUNT_REGISTRATION_FEE = Utils.toNanoCoins("0.01");
    public static BigInteger CREATE_OFFER_FEE = Utils.toNanoCoins("0.001");
    public static BigInteger TAKE_OFFER_FEE = CREATE_OFFER_FEE;

    private static final String registrationFee = "mvkDXt4QmN4Nq9dRUsRigBCaovde9nLkZR";
    private static final String offerFee = "n2upbsaKAe4PD3cc4JfS7UCqPC5oNd7Ckg";

    private final NetworkParameters params;

    @Inject
    public FeePolicy(NetworkParameters params)
    {
        this.params = params;
    }

    //TODO
    public Address getAddressForRegistrationFee()
    {
        try
        {
            return new Address(params, registrationFee);
        } catch (AddressFormatException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    //TODO
    public Address getAddressForOfferFee()
    {
        try
        {
            return new Address(params, offerFee);
        } catch (AddressFormatException e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
