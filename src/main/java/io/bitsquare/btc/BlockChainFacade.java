package io.bitsquare.btc;

import com.google.inject.Inject;

/**
 * That facade delivers blockchain functionality from the bitcoinJ library
 * Code from BitcoinJ must not be used outside that facade.
 * That way a change of the library will only affect that class.
 */
public class BlockChainFacade
{
    @Inject
    public BlockChainFacade()
    {

    }

    public boolean verifyAddressInBlockChain(String hashAsHexStringToVerify, String address)
    {
        return findAddressInBlockChain(address)
                && getDataForTxWithAddress(hashAsHexStringToVerify, address)
                && isFeePayed(address);
    }

    private boolean findAddressInBlockChain(String address)
    {
        // TODO
        // lookup for address in blockchain
        return true;
    }

    private boolean getDataForTxWithAddress(String hashToVerify, String address)
    {
        // TODO
        // check if data after OP_RETURN match hashToVerify
        return true;
    }

    private boolean isFeePayed(String address)
    {
        // TODO
        // check if fee is payed
        return true;
    }

}
