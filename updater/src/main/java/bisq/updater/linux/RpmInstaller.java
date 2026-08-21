package bisq.updater.linux;

import java.nio.file.Path;

import java.util.List;

import lombok.extern.slf4j.Slf4j;



import bisq.updater.InstallationFailedException;
import bisq.updater.linux.pkexec.PkExec;

@Slf4j
public class RpmInstaller {
    public static void install(Path rpmFile) {
        log.info("Installing {}", rpmFile);

        var cmd = List.of("rpm", "--upgrade", rpmFile.toAbsolutePath().toString());
        Process rpmProcess = PkExec.run(cmd);

        int exitCode = rpmProcess.exitValue();
        if (exitCode != 0) {
            throw new InstallationFailedException("Deb installation exited with " + exitCode);
        }

        log.info("Installation finished.");
    }
}
