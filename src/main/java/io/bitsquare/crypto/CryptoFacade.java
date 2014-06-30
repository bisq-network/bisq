package io.bitsquare.crypto;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Utils;
import com.google.common.base.Charsets;
import com.google.inject.Inject;
import java.security.SignatureException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * That facade delivers crypto functionality from the bitcoinJ library
 */
public class CryptoFacade
{
    private static final Logger log = LoggerFactory.getLogger(CryptoFacade.class);


    @Inject
    public CryptoFacade()
    {
    }


    public byte[] getEmbeddedAccountRegistrationData(@NotNull ECKey registrationKey, String stringifiedBankAccounts)
    {
        String signedBankAccountIDs = registrationKey.signMessage(stringifiedBankAccounts);
        return Utils.sha256hash160(concatenateChunks(stringifiedBankAccounts, signedBankAccountIDs).getBytes(Charsets.UTF_8));
    }

    public String signContract(@NotNull ECKey key, String contractAsJson)
    {
        return key.signMessage(contractAsJson);
    }

    //   registration
    public boolean verifySignature(byte[] pubKey, String msg, String sig)
    {
        try
        {
            @NotNull ECKey key = new ECKey(null, pubKey, true);
            key.verifyMessage(msg, sig);
            return true;
        } catch (SignatureException e)
        {
            return false;
        }
    }

    public boolean verifyHash(String hashAsHexStringToVerify, String msg, String sig)
    {
        String hashAsHexString = Utils.bytesToHexString(createHash(msg, sig));
        return hashAsHexString.equals(hashAsHexStringToVerify);
    }

    private byte[] createHash(String msg, String sig)
    {
        @NotNull byte[] hashBytes = concatenateChunks(msg, sig).getBytes(Charsets.UTF_8);
        return Utils.sha256hash160(hashBytes);
    }

    @NotNull
    private String concatenateChunks(String stringifiedBankAccounts, String signedBankAccountIDs)
    {
        return stringifiedBankAccounts + signedBankAccountIDs;
    }

}
