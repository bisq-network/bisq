package io.bitsquare.util;

import com.google.bitcoin.core.Utils;
import com.google.bitcoin.utils.Threading;
import io.bitsquare.BitSquare;
import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs in JavaFX Application Thread now but might be sent to a background thread.
 * We need to handle threading issues in the client classes if we change.
 */
public class DSAKeyUtil
{
    private static final Logger log = LoggerFactory.getLogger(DSAKeyUtil.class);
    private static final String prefix = BitSquare.getAppName() + "_";
    private static final ReentrantLock lock = Threading.lock("DSAKeyUtil");


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static String getHexStringFromPublicKey(PublicKey publicKey)
    {
        final X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());
        return Utils.bytesToHexString(x509EncodedKeySpec.getEncoded());
    }

    //  not used yet
    /*

    public static PublicKey getPublicKeyFromHexString(String publicKeyAsHex) throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        final byte[] bytes = Utils.parseAsHexOrBase58(publicKeyAsHex);
        final KeyFactory keyFactory = KeyFactory.GET_INSTANCE("DSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(bytes));
    } */


    public static KeyPair getKeyPair()
    {
        return getKeyPair(FileUtil.getFile(prefix + "public", "key"), FileUtil.getFile(prefix + "private", "key"));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    private static KeyPair getKeyPair(File pubKeyFile, File privKeyFile)
    {
        try
        {
            return readKeyPairFromFiles(pubKeyFile, privKeyFile);
        } catch (Throwable throwable)
        {
            if (throwable instanceof FileNotFoundException)
            {
                log.debug("Files not found. That is ok for the first execute.");
            }
            else
            {
                log.error("Could not read key files. " + throwable);
            }

            try
            {
                final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
                keyGen.initialize(1024);
                KeyPair generatedKeyPair = keyGen.genKeyPair();
                try
                {
                    saveKeyPairToFiles(pubKeyFile, privKeyFile, generatedKeyPair);
                } catch (Throwable t2)
                {
                    t2.printStackTrace();
                    log.error("Saving key pair failed. " + t2);
                }
                return generatedKeyPair;
            } catch (Throwable t3)
            {
                t3.printStackTrace();
                log.error("Generating key pair failed. " + t3);
                return null;
            }
        }
    }


    private static void saveKeyPairToFiles(File pubKeyFile, File privKeyFile, KeyPair keyPair) throws IOException
    {
        lock.lock();
        final File pubKeyTempFile = FileUtil.getTempFile("pubKey_temp_" + prefix);
        final File privKeyTempFile = FileUtil.getTempFile("privKey_temp_" + prefix);
        final FileOutputStream pubKeyFileOutputStream = new FileOutputStream(pubKeyTempFile);
        final FileOutputStream privKeyFileOutputStream = new FileOutputStream(privKeyTempFile);
        try
        {
            // Don't use auto closeable resources in try() as we need to close it
            // manually before replacing file with temp file anyway

            final PublicKey publicKey = keyPair.getPublic();
            final X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());
            pubKeyFileOutputStream.write(x509EncodedKeySpec.getEncoded());
            pubKeyFileOutputStream.flush();
            pubKeyFileOutputStream.getFD().sync();

            final PrivateKey privateKey = keyPair.getPrivate();
            final PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
            privKeyFileOutputStream.write(pkcs8EncodedKeySpec.getEncoded());
            privKeyFileOutputStream.flush();
            privKeyFileOutputStream.getFD().sync();

            // Close resources before replacing file with temp file because otherwise it causes problems on windows when rename temp file
            pubKeyFileOutputStream.close();
            privKeyFileOutputStream.close();

            FileUtil.writeTempFileToFile(pubKeyTempFile, pubKeyFile);
            FileUtil.writeTempFileToFile(privKeyTempFile, privKeyFile);
        } catch (IOException e)
        {
            e.printStackTrace();
            log.error("saveKeyPairToFiles failed." + e);

        } finally
        {
            if (pubKeyTempFile.exists())
            {
                log.warn("PubKeyTempFile still exists after failed save.");
                if (!pubKeyTempFile.delete())
                {
                    log.warn("Cannot delete pubKeyTempFile.");
                }
            }
            if (privKeyTempFile.exists())
            {
                log.warn("PrivKeyTempFile still exists after failed save.");
                if (!privKeyTempFile.delete())
                {
                    log.warn("Cannot delete privKeyTempFile.");
                }
            }

            try
            {
                pubKeyFileOutputStream.close();
                privKeyFileOutputStream.close();
            } catch (IOException e)
            {
                e.printStackTrace();
                log.error("Cannot close resources.");
            }

            lock.unlock();
        }
    }

    private static KeyPair readKeyPairFromFiles(File pubKeyFile, File privKeyFile) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException
    {
        lock.lock();
        try (final FileInputStream pubKeyFileInputStream = new FileInputStream(pubKeyFile);
             final FileInputStream privKeyFileInputStream = new FileInputStream(privKeyFile))
        {
            final KeyFactory keyFactory = KeyFactory.getInstance("DSA");

            final byte[] encodedPubKey = new byte[(int) pubKeyFile.length()];
            //noinspection ResultOfMethodCallIgnored
            pubKeyFileInputStream.read(encodedPubKey);
            final PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(encodedPubKey));

            final byte[] encodedPrivKey = new byte[(int) privKeyFile.length()];
            //noinspection ResultOfMethodCallIgnored
            privKeyFileInputStream.read(encodedPrivKey);
            final PrivateKey privKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encodedPrivKey));
            return new KeyPair(pubKey, privKey);
        } finally
        {
            lock.unlock();
        }
    }
}
