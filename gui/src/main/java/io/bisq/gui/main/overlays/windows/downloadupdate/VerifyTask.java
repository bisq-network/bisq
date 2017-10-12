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

/**
 * A Task to verify the downloaded bisq installer against the available keys/signatures.
 */

import com.google.common.collect.Lists;
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
     */
    public VerifyTask(final List<FileDescriptor> fileDescriptors) {
        super();
        this.fileDescriptors = fileDescriptors;
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

        // iterate all signatures available to us
        for (FileDescriptor sig : sigs) {
            VerifyDescriptor.VerifyDescriptorBuilder verifyDescriptorBuilder = VerifyDescriptor.builder().sigFile(sig.getSaveFile());
            // Sigs are linked to keys, extract all keys which have the same id
            List<FileDescriptor> keys = fileDescriptors.stream()
                    .filter(keyDescriptor -> DownloadType.KEY.equals(keyDescriptor.getType()))
                    .filter(keyDescriptor -> sig.getId().equals(keyDescriptor.getId()))
                    .collect(Collectors.toList());
            // iterate all keys which have the same id
            for (FileDescriptor key : keys) {
                verifyDescriptorBuilder.keyFile(key.getSaveFile());
                try {
                    verifyDescriptorBuilder.verifyStatusEnum(BisqInstaller.verifySignature(key.getSaveFile(), sig.getSaveFile(), installer.get().getSaveFile()));
                    updateMessage(key.getFileName());
                } catch (Exception e) {
                    verifyDescriptorBuilder.verifyStatusEnum(BisqInstaller.VerifyStatusEnum.FAIL);
                    log.error(e.toString());
                    e.printStackTrace();
                }
                verifyDescriptors.add(verifyDescriptorBuilder.build());
            }
        }
        return verifyDescriptors;
    }
}

