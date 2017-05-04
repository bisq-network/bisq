package io.bitsquare.common.util;

/**
 * A utility that downloads a file from a URL.
 * @author www.codejava.net
 *
 */


import javafx.concurrent.Task;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Security;
import java.security.SignatureException;
import java.util.Iterator;


public class DownloadUtil extends Task<File> {

    private static final int BUFFER_SIZE = 4096;
    private String fileName = null;
    private final String fileURL;
    private final String saveDir;
    private DownloadType downloadType;
    private byte index; // For numbering tasks to diferent pubkeys/signatures


    /**
     * Prepares a task to download a file from {@code fileURL} to {@code saveDir}.
     * @param fileURL HTTP URL of the file to be downloaded
     * @param saveDir path of the directory to save the file
     */
    public DownloadUtil(final String fileURL, final String saveDir, DownloadType downloadType, byte index) {
        super();
        this.fileURL = fileURL;
        this.saveDir = saveDir;
        this.downloadType = downloadType;
        this.index = index;
    }

    public DownloadUtil(final String fileURL, final String saveDir) {
        this(fileURL, saveDir, DownloadType.MISC, (byte) -1);
    }

    /**
     * Prepares a task to download a file from {@code fileURL} to the sysyem's temp dir specified in java.io.tmpdir.
     * @param fileURL HTTP URL of the file to be downloaded
     */
    public DownloadUtil(final String fileURL, DownloadType downloadType, byte index) {
        this(fileURL, "/home/bob/Downloads/", downloadType, index);
//TODO        final String saveDir = System.getProperty("java.io.tmpdir");
//        final String saveDir = "/home/bob/Downloads/";
        System.out.println("Auto-selected temp dir " + this.saveDir);
    }

    public DownloadUtil(final String fileURL) {
        this(fileURL, DownloadType.MISC, (byte) -1);
    }


        /**
         * Starts the task and therefore the actual download.
         * @return A reference to the created file or {@code null} if no file could be found at the provided URL
         * @throws IOException Forwarded exceotions from HttpURLConnection and file handling methods
         */
    @Override protected File call() throws IOException {
        System.out.println("Task started....");
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        // always check HTTP response code first
/*        if (responseCode == HttpURLConnection.HTTP_OK) {
            String fileName = "";
            String disposition = httpConn.getHeaderField("Content-Disposition");
            String contentType = httpConn.getContentType();
            int contentLength = httpConn.getContentLength();
            if (!(contentLength > 0))
                contentLength = -1;

            if (disposition != null) {
                // extracts file name from header field
                int index = disposition.indexOf("filename=");
                if (index > 0) {
                    this.fileName = disposition.substring(index + 9, disposition.length());
                }
            } else {
                // extracts file name from URL
*/                this.fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length());
/*            }

            // opens input stream from the HTTP connection
            InputStream inputStream = httpConn.getInputStream();

*/            String saveFilePath = saveDir + (saveDir.endsWith(File.separator) ? "" : File.separator) + fileName;

            // opens an output stream to save into file
            File outputFile = new File(saveFilePath);
/*            FileOutputStream outputStream = new FileOutputStream(outputFile);

            int bytesRead;
            int totalRead = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (this.isCancelled())
                    break;
                outputStream.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                updateProgress(totalRead, contentLength);
            }

            try {
                outputStream.close();
            } catch (IOException e) {
            } finally {
                inputStream.close();
            }
*/
            return outputFile;
/*        } else {
            System.out.println("No file to download. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
        return null;
*/    }

    /**
     * Verifies detached PGP signatures against GPG/openPGP RSA public keys. Does currently not work with openssl or JCA/JCE keys.
     * @param pubKeyFilename Path to file providing the public key to use
     * @param sigFilename Path to detached signature file
     * @param dataFilename Path to signed data file
     * @return {@code true} if signature is valid, {@code false} if signature is not valid
     * @throws Exception throws various exceptions in case something went wrong. Main reason should be that key or
     * signature could be extracted from the provided files due to a "bad" format.<br>
     * <code>FileNotFoundException, IOException, SignatureException, PGPException</code>
     */
    public static boolean verifySignature(File pubKeyFile, File sigFile, File dataFile) throws Exception {
//        private final String pubkeyFilename = "/home/bob/Downloads/F379A1C6.asc.gpg";
//        private final String sigFilename = "/home/bob/Downloads/sig.asc";
//        private final String dataFilename = "/home/bob/Downloads/Bitsquare-64bit-0.4.9.9.1.deb";

        Security.addProvider(new BouncyCastleProvider());
        InputStream is;
        int bytesRead;
        PGPPublicKey publicKey;
        PGPSignature pgpSignature;
        boolean result;


        // Read keys from file
        is = PGPUtil.getDecoderStream(new FileInputStream(pubKeyFile));
        PGPPublicKeyRingCollection publicKeyRingCollection = new PGPPublicKeyRingCollection(is);
        is.close();

        Iterator<PGPPublicKeyRing> rIt = publicKeyRingCollection.getKeyRings();
        PGPPublicKeyRing pgpPublicKeyRing;
        if (rIt.hasNext()) {
            pgpPublicKeyRing = rIt.next();
        } else {
            throw new PGPException("Could not find public keyring in provided key file");
        }

    // Would be the solution for multiple keys in one file
//        Iterator<PGPPublicKey> kIt;
//        kIt = pgpPublicKeyRing.getPublicKeys();
//        publicKey = pgpPublicKeyRing.getPublicKey(0xF5B84436F379A1C6L);

        // Read signature from file
        is = PGPUtil.getDecoderStream(new FileInputStream(sigFile));
        PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(is);
        Object o = pgpObjectFactory.nextObject();
        if (o instanceof PGPSignatureList) {
            PGPSignatureList signatureList = (PGPSignatureList) o;
            pgpSignature = signatureList.get(0);
        } else if (o instanceof PGPSignature) {
            pgpSignature = (PGPSignature) o;
        } else {
            throw new SignatureException("Could not find signature in provided signature file");
        }
        is.close();
        System.out.format("KeyID used in signature: %X\n", pgpSignature.getKeyID());
        publicKey = pgpPublicKeyRing.getPublicKey(pgpSignature.getKeyID());
        System.out.format("The ID of the selected key is %X\n", publicKey.getKeyID());
        pgpSignature.initVerify(publicKey, "BC");

        // Read file to verify
        try {
            byte[] data = new byte[1024];
            is = new DataInputStream(new BufferedInputStream(new FileInputStream(dataFile)));
            while (true) {
                bytesRead = is.read(data, 0, 1024);
                if (bytesRead == -1)
                    break;
                pgpSignature.update(data, 0, bytesRead);
            }
            is.close();

            // Verify the signature
            result = pgpSignature.verify();
            System.out.println("The signature turned out to be " + (result ? "good." : "bad."));
            return result;
        } catch (SignatureException sigExc) {
            try {
                is.close();
            } catch (IOException ioExc) {}
            throw sigExc;
        }
    }

    public String getFileName() {return this.fileName;}

    public DownloadType getDownloadType() {return downloadType;}

    public byte getIndex() {return index;}
}

