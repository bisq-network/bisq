/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.overlays.windows.downloadupdate;

import com.google.common.collect.Lists;
import io.bisq.common.util.Utilities;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BisqInstaller {
    static final String FINGER_PRINT_MANFRED_KARRER = "F379A1C6";
    static final String FINGER_PRINT_CHRIS_BEAMS = "5BC5ED73";
    static final String PUB_KEY_HOSTING_URL = "https://bisq.network/pubkey/";
    static final String DOWNLOAD_HOST_URL = "https://github.com/bisq-network/exchange/releases/download/";

    public boolean isSupportedOS() {
        return Utilities.isOSX() || Utilities.isWindows() || Utilities.isLinux();
    }

    public Optional<DownloadTask> download(String version) {
        String partialUrl = DOWNLOAD_HOST_URL + "v" + version + "/";

        // Get installer filename on all platforms
        FileDescriptor installerFileDescriptor = getInstallerDescriptor(version, partialUrl);
        // tells us which key was used for signing
        FileDescriptor signingKeyDescriptor = getSigningKeyDescriptor(partialUrl);
        List<FileDescriptor> keyFileDescriptors = getKeyFileDescriptors();
        List<FileDescriptor> sigFileDescriptors = getSigFileDescriptors(installerFileDescriptor, keyFileDescriptors);

        List<FileDescriptor> allFiles = Lists.newArrayList();
        allFiles.addAll(Lists.newArrayList(installerFileDescriptor));
        allFiles.addAll(Lists.newArrayList(signingKeyDescriptor));
        allFiles.addAll(keyFileDescriptors);
        allFiles.addAll(sigFileDescriptors);

        // Download keys, sigs and Installer
        return getDownloadTask(allFiles);
    }

    public VerifyTask verify(List<FileDescriptor> fileDescriptors) {
        VerifyTask verifyTask = new VerifyTask(fileDescriptors);
        Thread th = new Thread(verifyTask);
        th.start();
        // TODO: check for problems when creating task
        return verifyTask;
    }

    private Optional<DownloadTask> getDownloadTask(List<FileDescriptor> fileDescriptors) {
        try {
            return Optional.of(downloadFiles(fileDescriptors, Utilities.getDownloadOfHomeDir()));
        } catch (IOException exception) {
            return Optional.<DownloadTask>empty();
        }
    }

    /**
     * Creates and starts a Task for background downloading
     *
     * @param fileDescriptors
     * @param saveDir         Directory to save file to
     * @return The task handling the download
     * @throws IOException
     */
    public static DownloadTask downloadFiles(List<FileDescriptor> fileDescriptors, String saveDir) throws IOException {
        if (saveDir == null)
            saveDir = Utilities.getDownloadOfHomeDir();
        DownloadTask task = new DownloadTask(fileDescriptors, saveDir);
        Thread th = new Thread(task);
        th.start();
        // TODO: check for problems when creating task
        return task;
    }

    /**
     * Verifies detached PGP signatures against GPG/openPGP RSA public keys. Does currently not work with openssl or JCA/JCE keys.
     *
     * @param pubKeyFile Path to file providing the public key to use
     * @param sigFile    Path to detached signature file
     * @param dataFile   Path to signed data file
     * @return {@code true} if signature is valid, {@code false} if signature is not valid
     * @throws Exception throws various exceptions in case something went wrong. Main reason should be that key or
     *                   signature could be extracted from the provided files due to a "bad" format.<br>
     *                   <code>FileNotFoundException, IOException, SignatureException, PGPException</code>
     */
    public static VerifyStatusEnum verifySignature(File pubKeyFile, File sigFile, File dataFile) throws Exception {
        InputStream inputStream;
        int bytesRead;
        PGPPublicKey publicKey;
        PGPSignature pgpSignature;
        boolean result;

        // Read keys from file
        inputStream = PGPUtil.getDecoderStream(new FileInputStream(pubKeyFile));
        PGPPublicKeyRingCollection publicKeyRingCollection = new PGPPublicKeyRingCollection(inputStream, new JcaKeyFingerprintCalculator());
        inputStream.close();

        Iterator<PGPPublicKeyRing> iterator = publicKeyRingCollection.getKeyRings();
        PGPPublicKeyRing pgpPublicKeyRing;
        if (iterator.hasNext()) {
            pgpPublicKeyRing = iterator.next();
        } else {
            throw new PGPException("Could not find public keyring in provided key file");
        }

        // Would be the solution for multiple keys in one file
        //        Iterator<PGPPublicKey> kIt;
        //        kIt = pgpPublicKeyRing.getPublicKeys();
        //        publicKey = pgpPublicKeyRing.getPublicKey(0xF5B84436F379A1C6L);

        // Read signature from file
        inputStream = PGPUtil.getDecoderStream(new FileInputStream(sigFile));
        PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(inputStream, new JcaKeyFingerprintCalculator());
        Object o = pgpObjectFactory.nextObject();
        if (o instanceof PGPSignatureList) {
            PGPSignatureList signatureList = (PGPSignatureList) o;
            checkArgument(!signatureList.isEmpty(), "signatureList must not be empty");
            pgpSignature = signatureList.get(0);
        } else if (o instanceof PGPSignature) {
            pgpSignature = (PGPSignature) o;
        } else {
            throw new SignatureException("Could not find signature in provided signature file");
        }
        inputStream.close();
        log.debug("KeyID used in signature: %X\n", pgpSignature.getKeyID());
        publicKey = pgpPublicKeyRing.getPublicKey(pgpSignature.getKeyID());

        // If signature is not matching the key used for signing we fail
        if (publicKey == null)
            return VerifyStatusEnum.FAIL;

        log.debug("The ID of the selected key is %X\n", publicKey.getKeyID());
        pgpSignature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);

        // Read file to verify
        byte[] data = new byte[1024];
        inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(dataFile)));
        while (true) {
            bytesRead = inputStream.read(data, 0, 1024);
            if (bytesRead == -1)
                break;
            pgpSignature.update(data, 0, bytesRead);
        }
        inputStream.close();

        // Verify the signature
        result = pgpSignature.verify();
        return result ? VerifyStatusEnum.OK : VerifyStatusEnum.FAIL;
    }

    @NotNull
    public FileDescriptor getInstallerDescriptor(String version, String partialUrl) {
        String fileName;
        String prefix = "Bisq-";
        // https://github.com/bisq-network/exchange/releases/download/v0.5.1/Bisq-0.5.1.dmg
        if (Utilities.isOSX())
            fileName = prefix + version + ".dmg";
        else if (Utilities.isWindows())
            fileName = prefix + Utilities.getOSArchitecture() + "bit-" + version + ".exe";
        else if (Utilities.isLinux())
            fileName = prefix + Utilities.getOSArchitecture() + "bit-" + version + ".deb";
        else
            throw new RuntimeException("No suitable OS found, use osCheck before calling this method.");

        return FileDescriptor.builder()
                .type(DownloadType.INSTALLER)
                .fileName(fileName)
                .id(fileName)
                .loadUrl(partialUrl.concat(fileName))
                .build();
    }

    @NotNull
    public FileDescriptor getSigningKeyDescriptor(String url) {
        String fileName = "signingkey.asc";
        return FileDescriptor.builder()
                .type(DownloadType.SIGNING_KEY)
                .fileName(fileName)
                .id(fileName)
                .loadUrl(url.concat(fileName))
                .build();
    }


    /**
     * The files containing the gpg keys of the bisq signers.
     * Currently these are 2 hard-coded keys, one included with bisq and the same key online for maximum security.
     *
     * @return list of keys to check agains corresponding sigs.
     */
    public List<FileDescriptor> getKeyFileDescriptors() {
        List<FileDescriptor> list = new ArrayList<>();

        list.add(getKeyFileDescriptor(FINGER_PRINT_MANFRED_KARRER));
        list.add(getLocalKeyFileDescriptor(FINGER_PRINT_MANFRED_KARRER));

        list.add(getKeyFileDescriptor(FINGER_PRINT_CHRIS_BEAMS));
        list.add(getLocalKeyFileDescriptor(FINGER_PRINT_CHRIS_BEAMS));

        return list;
    }

    private FileDescriptor getKeyFileDescriptor(String fingerPrint) {
        final String fileName = fingerPrint + ".asc";
        return FileDescriptor.builder()
                .type(DownloadType.KEY)
                .fileName(fileName)
                .id(fingerPrint)
                .loadUrl(PUB_KEY_HOSTING_URL + fileName)
                .build();
    }

    private FileDescriptor getLocalKeyFileDescriptor(String fingerPrint) {
        return FileDescriptor.builder()
                .type(DownloadType.KEY)
                .fileName(fingerPrint + ".asc-local")
                .id(fingerPrint)
                .loadUrl(getClass().getResource("/keys/" + fingerPrint + ".asc").toExternalForm())
                .build();
    }

    /**
     * There is one installer file, X keys and X sigs. The id links the sig to its key.
     * If we switch to multiple keys, the filename should also be key-dependent (filename.F1234.asc).
     *
     * @param installerFileDescriptor which installer file should this signatures be linked to?
     * @return
     */
    public List<FileDescriptor> getSigFileDescriptors(FileDescriptor installerFileDescriptor, List<FileDescriptor> keys) {
        String suffix = ".asc";
        List<FileDescriptor> result = Lists.newArrayList();

        for (FileDescriptor key : keys) {
            if (!result.stream().filter(e -> e.getId().equals(key.getId())).findAny().isPresent()) {
                result.add(FileDescriptor.builder()
                        .type(DownloadType.SIG)
                        .fileName(installerFileDescriptor.getFileName().concat(suffix))
                        .id(key.getId())
                        .loadUrl(installerFileDescriptor.getLoadUrl().concat(suffix))
                        .build());
            } else {
                log.debug("We have already a file with the key: " + key.getId());
            }
        }
        return result;
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
        SIGNING_KEY,
        MISC
    }
}

