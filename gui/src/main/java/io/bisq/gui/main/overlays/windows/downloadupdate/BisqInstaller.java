package io.bisq.gui.main.overlays.windows.downloadupdate;

import com.google.common.collect.Lists;
import io.bisq.common.util.Utilities;
import javafx.scene.control.ProgressIndicator;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.*;
import java.security.Security;
import java.security.SignatureException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Slf4j
public class BisqInstaller {
    private List<String> knownKeys = Lists.newArrayList("F379A1C6");

    public boolean osCheck() {
        return Utilities.isOSX() || Utilities.isWindows() || Utilities.isLinux();
    }

    public Optional<DownloadTask> download(String version, ProgressIndicator progressIndicator) {
        String partialUrl = "https://github.com/bitsquare/bitsquare/releases/download/v" + version + "/";

        // Get installer filename on all platforms
        FileDescriptor installerFileDescriptor = getInstallerDescriptor(version, partialUrl);
        List<FileDescriptor> keyFileDescriptors = getKeyFileDescriptors();
        List<FileDescriptor> sigFileDescriptors = getSigFileDescriptors(installerFileDescriptor, version);

        List<FileDescriptor> allFiles = Lists.newArrayList();
        allFiles.addAll(keyFileDescriptors);
        allFiles.addAll(sigFileDescriptors);
        allFiles.addAll(Lists.newArrayList(installerFileDescriptor));

        // Download keys, sigs and Installer
        Optional<DownloadTask> installerDownloadTask = getDownloadTask(allFiles, progressIndicator);

        return installerDownloadTask;
    }

    public VerifyTask verify(List<FileDescriptor> fileDescriptors) {
        VerifyTask verifyTask = new VerifyTask(fileDescriptors);
        Thread th = new Thread(verifyTask);
        th.start();
        // TODO: check for problems when creating task
        return verifyTask;
    }

    private Optional<DownloadTask> getDownloadTask(List<FileDescriptor> fileDescriptors, ProgressIndicator progressIndicator) {
        DownloadTask result = null;
        try {
            result = downloadFiles(fileDescriptors, Utilities.getTmpDir(), progressIndicator);
        } catch (IOException exception) {
            return Optional.empty();
        }

        return Optional.of(result);
    }

    /**
     * Creates and starts a Task for background downloading
     *
     * @param fileURL      URL of file to be downloaded
     * @param saveDir      Directory to save file to
     * @param indicator    Progress indicator, can be {@code null}
     * @param downloadType enum to identify downloaded files after completion, options are {INSTALLER, KEY, SIG, MISC}
     * @param index        For coordination between key and sig files
     * @return The task handling the download
     * @throws IOException
     */
    public static DownloadTask downloadFiles(List<FileDescriptor> fileDescriptors, String saveDir, @Nullable ProgressIndicator indicator) throws IOException {
        DownloadTask task;
        if (saveDir != null)
            task = new DownloadTask(fileDescriptors, saveDir);
        else
            task = new DownloadTask(fileDescriptors, Utilities.getTmpDir()); // Tries to use system temp directory
        if (indicator != null) {
            indicator.progressProperty().unbind();
            indicator.progressProperty().bind(task.progressProperty());
        }
        Thread th = new Thread(task);
        th.start();
        // TODO: check for problems when creating task
        return task;
    }

    /**
     * Verifies detached PGP signatures against GPG/openPGP RSA public keys. Does currently not work with openssl or JCA/JCE keys.
     *
     * @param pubKeyFilename Path to file providing the public key to use
     * @param sigFilename    Path to detached signature file
     * @param dataFilename   Path to signed data file
     * @return {@code true} if signature is valid, {@code false} if signature is not valid
     * @throws Exception throws various exceptions in case something went wrong. Main reason should be that key or
     *                   signature could be extracted from the provided files due to a "bad" format.<br>
     *                   <code>FileNotFoundException, IOException, SignatureException, PGPException</code>
     */
    public static VerifyStatusEnum verifySignature(File pubKeyFile, File sigFile, File dataFile) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        InputStream is;
        int bytesRead;
        PGPPublicKey publicKey;
        PGPSignature pgpSignature;
        boolean result;

        // Read keys from file
        is = PGPUtil.getDecoderStream(new FileInputStream(pubKeyFile));
        PGPPublicKeyRingCollection publicKeyRingCollection = new PGPPublicKeyRingCollection(is, new JcaKeyFingerprintCalculator());
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
        PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(is, new JcaKeyFingerprintCalculator());
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
        log.debug("KeyID used in signature: %X\n", pgpSignature.getKeyID());
        publicKey = pgpPublicKeyRing.getPublicKey(pgpSignature.getKeyID());
        log.debug("The ID of the selected key is %X\n", publicKey.getKeyID());
        pgpSignature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);

        // Read file to verify
        //try {
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
        return result ? VerifyStatusEnum.OK : VerifyStatusEnum.FAIL;
    }


    @NotNull
    private FileDescriptor getInstallerDescriptor(String version, String partialUrl) {
        String fileName = null;
        String prefix = "Bisq-";
        // https://github.com/bitsquare/bitsquare/releases/download/v0.5.1/Bisq-0.5.1.dmg
        if (Utilities.isOSX())
            fileName = prefix + version + ".dmg";
        else if (Utilities.isWindows())
            fileName = prefix + Utilities.getOSArchitecture() + "bit-" + version + ".exe";
        else if (Utilities.isLinux())
            fileName = prefix + Utilities.getOSArchitecture() + "bit-" + version + ".deb";
        else
            log.error("No suitable OS found, use osCheck before calling this method.");

        return FileDescriptor.builder()
                .type(DownloadType.INSTALLER)
                .fileName(fileName).id(fileName).loadUrl(partialUrl.concat(fileName)).build();
    }

    public List<FileDescriptor> getKeyFileDescriptors() {
        String fingerprint = "F379A1C6";
        String fileName = fingerprint + ".asc";
        String fixedKeyPath = "/keys/" + fileName;
        return Lists.newArrayList(
                FileDescriptor.builder()
                        .type(DownloadType.KEY)
                        .fileName(fileName)
                        .id(fingerprint)
                        .loadUrl("https://bisq.io/pubkey/" + fileName).build(),
                FileDescriptor.builder()
                        .type(DownloadType.KEY)
                        .fileName(fileName + "-local")
                        .id(fingerprint)
                        .loadUrl(getClass().getResource(fixedKeyPath).toExternalForm())
                        .build()
        );
    }

    public List<FileDescriptor> getSigFileDescriptors(FileDescriptor installerFileDescriptor, String version) {
        String suffix = ".asc";
        return Lists.newArrayList(FileDescriptor.builder()
                .type(DownloadType.SIG)
                .fileName(installerFileDescriptor.getFileName().concat(suffix))
                .id("F379A1C6")
                .loadUrl(installerFileDescriptor.getLoadUrl().concat(suffix))
                .build());
    }

    @Data
    @Builder
    public static class FileDescriptor {
        private String id;
        private DownloadType type;
        private String loadUrl;
        private String fileName;
        private File saveFile;
        @Builder.Default
        private DownloadStatusEnum downloadStatus = DownloadStatusEnum.UNKNOWN;
    }

    @Data
    @Builder
    public static class VerifyDescriptor {
        private File keyFile;
        private File sigFile;
        @Builder.Default
        private VerifyStatusEnum verifyStatusEnum = VerifyStatusEnum.UNKNOWN;
    }

    public enum DownloadStatusEnum {
        OK, FAIL, TIMEOUT, UNKNOWN
    }

    public enum VerifyStatusEnum {
        OK, FAIL, UNKNOWN
    }

    public enum DownloadType {
        INSTALLER,
        KEY,
        SIG,
        MISC
    }
}

