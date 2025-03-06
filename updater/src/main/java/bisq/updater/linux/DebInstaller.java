package bisq.updater.linux;

import java.nio.file.Path;

import java.util.List;

import lombok.extern.slf4j.Slf4j;



import bisq.updater.InstallationFailedException;
import bisq.updater.linux.pkexec.PkExec;

@Slf4j
public class DebInstaller {
    public static void install(Path debFile) {
        log.info("Installing {}", debFile);

        var cmd = List.of("dpkg", "--install", debFile.toAbsolutePath().toString());
        Process dpkgProcess = PkExec.run(cmd);

        int exitCode = dpkgProcess.exitValue();
        if (exitCode != 0) {
            throw new InstallationFailedException("Deb installation exited with " + exitCode);
        }

        log.info("Installation finished.");
    }
}
