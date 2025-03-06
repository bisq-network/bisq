package bisq.updater.windows;

import java.nio.file.Path;

import java.io.IOException;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;



import bisq.updater.InstallationFailedException;

@Slf4j
public class MsiInstaller {
    public static void install(Path msiFile, Path logFile) {
        try {
            log.info("Installing {}", msiFile);

            var cmd = List.of("msiexec",
                    "/package", msiFile.toAbsolutePath().toString(),
                    "/passive",
                    "/norestart",
                    "/log", logFile.toAbsolutePath().toString());

            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            boolean isSuccess = process.waitFor(2, TimeUnit.MINUTES);
            if (!isSuccess) {
                throw new InstallationFailedException("MSI installation didn't finish after 2 minutes.");
            }

        } catch (IOException | InterruptedException e) {
            throw new InstallationFailedException("MSI installation failed.", e);
        } finally {
            log.info("Installation finished.");
        }
    }
}
