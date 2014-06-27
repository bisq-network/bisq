package io.bitsquare.util;

import com.google.bitcoin.core.Utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DSAKeyUtil
{
    private static final Logger log = LoggerFactory.getLogger(DSAKeyUtil.class);
    private static final String baseDir = Utilities.getRootDir();

    public static KeyPair getKeyPair()
    {
        return getKeyPair("public.key", "private.key");
    }

    public static KeyPair getKeyPair(String keyName)
    {
        return getKeyPair(keyName + "_public" + ".key", keyName + "_private" + ".key");
    }


    public static String getHexStringFromPublicKey(PublicKey publicKey)
    {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());
        return Utils.bytesToHexString(x509EncodedKeySpec.getEncoded());
    }

    public static PublicKey getPublicKeyFromHexString(String publicKeyAsHex)
    {
        byte[] bytes = Utils.parseAsHexOrBase58(publicKeyAsHex);
        try
        {
            KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return null;
    }

    public static KeyPair getKeyPair(String pubKeyPath, String privKeyPath)
    {
        try
        {
            return loadKeyPair(pubKeyPath, privKeyPath, "DSA");
        } catch (Exception e)
        {
            try
            {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
                keyGen.initialize(1024);
                KeyPair generatedKeyPair = keyGen.genKeyPair();

                // System.out.println("Generated Key Pair");
                dumpKeyPair(generatedKeyPair);
                saveKeyPair(pubKeyPath, privKeyPath, generatedKeyPair);
                return generatedKeyPair;
            } catch (Exception e2)
            {
                e2.printStackTrace();
                return null;
            }
        }
    }

    private static void dumpKeyPair(KeyPair keyPair)
    {
        PublicKey pub = keyPair.getPublic();
        // System.out.println("Public Key: " + getHexString(pub.getEncoded()));

        PrivateKey priv = keyPair.getPrivate();
        // System.out.println("Private Key: " + getHexString(priv.getEncoded()));
    }

    private static String getHexString(byte[] b)
    {
        String result = "";
        for (byte aB : b)
        {
            result += Integer.toString((aB & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }


    public static void saveKeyPair(String pubKeyPath, String privKeyPath, KeyPair keyPair) throws IOException
    {
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // Store Public Key.
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());
        FileOutputStream fos = new FileOutputStream(baseDir + pubKeyPath);
        fos.write(x509EncodedKeySpec.getEncoded());
        fos.close();

        // Store Private Key.
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
        fos = new FileOutputStream(baseDir + privKeyPath);
        fos.write(pkcs8EncodedKeySpec.getEncoded());
        fos.close();
    }


    public static KeyPair loadKeyPair(String pubKeyPath, String privKeyPath, String algorithm)
            throws IOException, NoSuchAlgorithmException,
            InvalidKeySpecException
    {
        // Read Public Key.
        File filePublicKey = new File(baseDir + pubKeyPath);
        FileInputStream fis = new FileInputStream(baseDir + pubKeyPath);
        byte[] encodedPublicKey = new byte[(int) filePublicKey.length()];
        fis.read(encodedPublicKey);
        fis.close();

        // Read Private Key.
        File filePrivateKey = new File(baseDir + privKeyPath);
        fis = new FileInputStream(baseDir + privKeyPath);
        byte[] encodedPrivateKey = new byte[(int) filePrivateKey.length()];
        fis.read(encodedPrivateKey);
        fis.close();

        // Generate KeyPair.
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(
                encodedPublicKey);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(
                encodedPrivateKey);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        return new KeyPair(publicKey, privateKey);
    }

}
