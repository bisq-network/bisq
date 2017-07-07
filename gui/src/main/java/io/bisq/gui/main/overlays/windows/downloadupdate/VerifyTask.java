package io.bisq.gui.main.overlays.windows.downloadupdate;

/**
 * A Task to verify the downloaded bisq installer against the available keys/signatures.
 */

import com.google.common.collect.Lists;
import io.bisq.common.locale.Res;
import io.bisq.gui.main.overlays.windows.downloadupdate.BisqInstaller.DownloadType;
import io.bisq.gui.main.overlays.windows.downloadupdate.BisqInstaller.FileDescriptor;
import io.bisq.gui.main.overlays.windows.downloadupdate.BisqInstaller.VerifyDescriptor;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class VerifyTask extends Task<List<VerifyDescriptor>> {
    private final List<FileDescriptor> fileDescriptors;

    /**
     * Prepares a task to download a file from {@code fileDescriptors} to {@code saveDir}.
     *
     * @param fileDescriptors HTTP URL of the file to be downloaded
     * @param saveDir         path of the directory to save the file
     */
    public VerifyTask(final List<FileDescriptor> fileDescriptors) {
        super();
        this.fileDescriptors = fileDescriptors;
        updateMessage(Res.get("displayUpdateDownloadWindow.verify.starting"));
        log.info("Starting VerifyTask with files:{}", fileDescriptors);
    }

    /**
     * Starts the task and therefore the actual download.
     *
     * @return A reference to the created file or {@code null} if no file could be found at the provided URL
     * @throws IOException Forwarded exceotions from HttpURLConnection and file handling methods
     */
    @Override
    protected List<VerifyDescriptor> call() throws IOException {
        log.debug("VerifyTask started...");
        Optional<FileDescriptor> installer = fileDescriptors.stream()
                .filter(fileDescriptor -> DownloadType.INSTALLER.equals(fileDescriptor.getType()))
                .findFirst();
        if (!installer.isPresent()) {
            log.error("No installer file found.");
            return Lists.newArrayList();
        }

        List<FileDescriptor> sigs = fileDescriptors.stream().filter(fileDescriptor -> DownloadType.SIG.equals(fileDescriptor.getType())).collect(Collectors.toList());
        List<VerifyDescriptor> verifyDescriptors = Lists.newArrayList();
        for (FileDescriptor sig : sigs) {
            VerifyDescriptor.VerifyDescriptorBuilder verifyDescriptorBuilder = VerifyDescriptor.builder().sigFile(sig.getSaveFile());
            List<FileDescriptor> keys = fileDescriptors.stream()
                    .filter(fileDescriptor -> DownloadType.KEY.equals(fileDescriptor.getType()))
                    .filter(fileDescriptor -> sig.getId().equals(fileDescriptor.getId()))
                    .collect(Collectors.toList());
            for(FileDescriptor key : keys) {
                updateMessage(Res.get("displayUpdateDownloadWindow.verify.files", key.getFileName()));
                verifyDescriptorBuilder.keyFile(key.getSaveFile());
                try {
                    verifyDescriptorBuilder.verifyStatusEnum(BisqInstaller.verifySignature(key.getSaveFile(), sig.getSaveFile(), installer.get().getSaveFile()));
                } catch (Exception e) {
                    verifyDescriptorBuilder.verifyStatusEnum(BisqInstaller.VerifyStatusEnum.FAIL);
                }
                verifyDescriptors.add(verifyDescriptorBuilder.build());
            }
        }

        return verifyDescriptors;
    }
}

