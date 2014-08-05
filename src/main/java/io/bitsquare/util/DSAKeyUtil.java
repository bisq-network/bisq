package io.bitsquare.util;

import com.google.bitcoin.core.Utils;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DSAKeyUtil
{
    private static final Logger log = LoggerFactory.getLogger(DSAKeyUtil.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static String getHexStringFromPublicKey(PublicKey publicKey)
    {
        final X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());
        return Utils.HEX.encode(x509EncodedKeySpec.getEncoded());
    }

    public static KeyPair generateKeyPair()
    {
        try
        {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
            keyGen.initialize(1024);
            KeyPair generatedKeyPair = keyGen.genKeyPair();
            return generatedKeyPair;
        } catch (NoSuchAlgorithmException e)
        {
            log.error(e.toString());
        }
        return null;
    }
}
