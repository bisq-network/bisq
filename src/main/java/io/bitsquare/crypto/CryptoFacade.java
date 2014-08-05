package io.bitsquare.crypto;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.crypto.KeyCrypterException;
import com.google.common.base.Charsets;
import java.nio.charset.Charset;
import java.security.SignatureException;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.util.encoders.Base64;

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

    // DeterministicKey does not support signMessage yet.
    private String signMessage(ECKey key, String message, @Nullable KeyParameter aesKey) throws KeyCrypterException
    {
        byte[] data = Utils.formatMessageForSigning(message);
        Sha256Hash hash = Sha256Hash.createDouble(data);
        ECKey.ECDSASignature sig = key.sign(hash, aesKey);
        // Now we have to work backwards to figure out the recId needed to recover the signature.
        int recId = -1;
        for (int i = 0; i < 4; i++)
        {
            ECKey k = ECKey.recoverFromSignature(i, sig, hash, key.isCompressed());
            if (k != null && k.getPubKeyPoint().equals(key.getPubKeyPoint()))
            {
                recId = i;
                break;
            }
        }
        if (recId == -1)
            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
        int headerByte = recId + 27 + (key.isCompressed() ? 4 : 0);
        byte[] sigData = new byte[65];  // 1 header + 32 bytes for R + 32 bytes for S
        sigData[0] = (byte) headerByte;
        System.arraycopy(Utils.bigIntegerToBytes(sig.r, 32), 0, sigData, 1, 32);
        System.arraycopy(Utils.bigIntegerToBytes(sig.s, 32), 0, sigData, 33, 32);
        return new String(Base64.encode(sigData), Charset.forName("UTF-8"));
    }

    public byte[] getEmbeddedAccountRegistrationData(ECKey registrationKey, String stringifiedBankAccounts)
    {
        String signedBankAccountIDs = signMessage(registrationKey, stringifiedBankAccounts, null);
        return Utils.sha256hash160(concatenateChunks(stringifiedBankAccounts, signedBankAccountIDs).getBytes(Charsets.UTF_8));
    }

    public String signContract(ECKey key, String contractAsJson)
    {
        return signMessage(key, contractAsJson, null);
    }

    //   registration
    public boolean verifySignature(byte[] pubKey, String msg, String sig)
    {
        try
        {
            ECKey key = ECKey.fromPublicOnly(pubKey);
            key.verifyMessage(msg, sig);
            return true;
        } catch (SignatureException e)
        {
            return false;
        }
    }

    public boolean verifyHash(String hashAsHexStringToVerify, String msg, String sig)
    {
        String hashAsHexString = Utils.HEX.encode(createHash(msg, sig));
        return hashAsHexString.equals(hashAsHexStringToVerify);
    }

    private byte[] createHash(String msg, String sig)
    {
        byte[] hashBytes = concatenateChunks(msg, sig).getBytes(Charsets.UTF_8);
        return Utils.sha256hash160(hashBytes);
    }


    private String concatenateChunks(String stringifiedBankAccounts, String signedBankAccountIDs)
    {
        return stringifiedBankAccounts + signedBankAccountIDs;
    }

}
