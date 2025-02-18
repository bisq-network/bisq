package bisq.updater.linux.pkexec;

import java.io.IOException;

import java.util.List;
import java.util.concurrent.TimeUnit;


import lombok.extern.slf4j.Slf4j;



import bisq.updater.InstallationFailedException;

@Slf4j
public class PkExec {
    public static final int AUTHORIZATION_FAILED = 127;
    private static final int AUTHORIZATION_DIALOG_DISMISSED = 126;

    public static Process run(List<String> args) {
        try {
            var processBuilder = new ProcessBuilder("pkexec");
            processBuilder.command().addAll(args);

            Process process = processBuilder
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            boolean isSuccess = process.waitFor(2, TimeUnit.MINUTES);
            if (!isSuccess) {
                throw new InstallationFailedException(processBuilder.command() + " didn't finish after 2 minutes.");
            }

            int exitCode = process.exitValue();
            switch (exitCode) {
                case AUTHORIZATION_FAILED:
                    throw new PkexecAuthorizationFailedException("Couldn't get authorization from user.");
                case AUTHORIZATION_DIALOG_DISMISSED:
                    throw new PkexecAuthorizationFailedException("User dismissed authorization dialog.");
            }

            return process;

        } catch (IOException | InterruptedException e) {
            throw new InstallationFailedException("Installation failed.", e);
        }
    }
}
