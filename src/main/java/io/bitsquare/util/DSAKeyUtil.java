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

public class DSAKeyUtil
{
    private static final Logger log = LoggerFactory.getLogger(DSAKeyUtil.class);
    private static final String prefix = BitSquare.ID + "_";
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
                log.debug("Files not found. That is ok for the first execute.");
            else
                log.error("Could not read key files. " + throwable);

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
        try (final FileOutputStream pubKeyFileOutputStream = new FileOutputStream(pubKeyTempFile);
             final FileOutputStream privKeyFileOutputStream = new FileOutputStream(privKeyTempFile))
        {

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

            if (Utils.isWindows())
            {
                // Work around an issue on Windows whereby you can't rename over existing files.
                final File pubKeyCanonicalFile = pubKeyFile.getCanonicalFile();
                if (pubKeyCanonicalFile.exists() && !pubKeyCanonicalFile.delete())
                    throw new IOException("Failed to delete pubKeyCanonicalFile for replacement with save");
                if (!pubKeyTempFile.renameTo(pubKeyCanonicalFile))
                    throw new IOException("Failed to rename " + pubKeyTempFile + " to " + pubKeyCanonicalFile);

                final File privKeyCanonicalFile = privKeyFile.getCanonicalFile();
                if (privKeyCanonicalFile.exists() && !privKeyCanonicalFile.delete())
                    throw new IOException("Failed to delete privKeyCanonicalFile for replacement with save");
                if (!privKeyTempFile.renameTo(privKeyCanonicalFile))
                    throw new IOException("Failed to rename " + privKeyTempFile + " to " + privKeyCanonicalFile);
            }
            else
            {
                if (!pubKeyTempFile.renameTo(pubKeyFile))
                {
                    throw new IOException("Failed to rename " + pubKeyTempFile + " to " + pubKeyFile);
                }
                if (!privKeyTempFile.renameTo(privKeyFile))
                {
                    throw new IOException("Failed to rename " + privKeyTempFile + " to " + privKeyFile);
                }
            }
        } finally
        {
            if (pubKeyTempFile.exists())
            {
                log.warn("PubKeyTempFile still exists after failed save.");
                if (!pubKeyTempFile.delete())
                    log.warn("Cannot delete pubKeyTempFile.");
            }
            if (privKeyTempFile.exists())
            {
                log.warn("PrivKeyTempFile still exists after failed save.");
                if (!privKeyTempFile.delete())
                    log.warn("Cannot delete privKeyTempFile.");
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
