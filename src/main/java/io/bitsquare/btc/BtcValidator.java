package io.bitsquare.btc;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;
import javax.inject.Inject;

public class BtcValidator
{
    private static NetworkParameters params;

    @Inject
    public BtcValidator(NetworkParameters params)
    {
        BtcValidator.params = params;
    }

    public static boolean isMinSpendableAmount(Coin amount)
    {
        return amount != null && amount.compareTo(FeePolicy.TX_FEE.add(Transaction.MIN_NONDUST_OUTPUT)) > 0;
    }

   /* public boolean isAddressValid(String addressString)
    {
        try
        {
            new Address(BtcValidator.params, addressString);
            return true;
        } catch (AddressFormatException e)
        {
            return false;
        }
    } */

}
