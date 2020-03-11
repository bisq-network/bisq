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

package bisq.core.app;

import bisq.core.user.Preferences;

import bisq.common.config.Config;
import bisq.common.storage.FileUtil;
import bisq.common.storage.ResourceNotFoundException;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;



import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

@Slf4j
@Singleton
public class AvoidStandbyModeService {

    private final Preferences preferences;
    private final Config config;
    private final Optional<String> inhibitorPathSpec;
    private CountDownLatch cancelInhibitSuspendLatch;
    private volatile boolean isStopped;


    @Inject
    public AvoidStandbyModeService(Preferences preferences, Config config) {
        this.preferences = preferences;
        this.config = config;
        this.inhibitorPathSpec = inhibitorPath();
        preferences.getUseStandbyModeProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                isStopped = true;
                log.info("AvoidStandbyModeService stopped");
            } else {
                start();
            }
        });
    }

    public void init() {
        isStopped = preferences.isUseStandbyMode();
        if (!isStopped) {
            start();
        }
    }

    private void start() {
        isStopped = false;
        log.info("AvoidStandbyModeService started");
        if (Utilities.isLinux()) {
            startInhibitor();
            new Thread(this::stopInhibitor, "StopAvoidStandbyModeService-thread").start();
        } else {
            new Thread(this::play, "AvoidStandbyModeService-thread").start();
        }
    }

    private void startInhibitor() {
        try {
            inhibitCommand().ifPresent(cmd -> {
                try {
                    final Process process = new ProcessBuilder(cmd).start();
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        if (process.isAlive()) {
                            process.destroy();
                            log.info("AvoidStandbyModeService shutdown");
                        }
                    }, "AvoidStandbyModeService.ShutDownHook"));
                    cancelInhibitSuspendLatch = new CountDownLatch(1);
                    log.info("disabled power management via {}", String.join(" ", cmd));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            log.error("could not use inhibitor to avoid standby mode", e);
        }
    }

    private void stopInhibitor() {
        try {
            if (!isStopped) {
                Objects.requireNonNull(cancelInhibitSuspendLatch).await();
            }
            inhibitorPathSpec.ifPresent(cmd -> {
                Optional<ProcessHandle> javaProcess = ProcessHandle.of(ProcessHandle.current().pid());
                javaProcess.ifPresent(process -> process.children().forEach(childProcess -> childProcess.info().command().ifPresent(command -> {
                    if (command.equals(cmd) && childProcess.isAlive()) {
                        childProcess.destroy();
                        log.info("AvoidStandbyModeService stopped");
                    }
                })));
            });
            Objects.requireNonNull(cancelInhibitSuspendLatch).countDown();
        } catch (Exception e) {
            log.error("stop inhibitor thread interrupted", e);
        }
    }

    private void play() {
        try {
            while (!isStopped) {
                try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(getSoundFile());
                     SourceDataLine sourceDataLine = getSourceDataLine(audioInputStream.getFormat())) {
                    byte[] tempBuffer = new byte[10000];
                    sourceDataLine.open(audioInputStream.getFormat());
                    sourceDataLine.start();
                    int cnt;
                    while ((cnt = audioInputStream.read(tempBuffer, 0, tempBuffer.length)) != -1 && !isStopped) {
                        if (cnt > 0) {
                            sourceDataLine.write(tempBuffer, 0, cnt);
                        }
                    }
                    sourceDataLine.drain();
                }
            }
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
        }
    }

    private File getSoundFile() throws IOException, ResourceNotFoundException {
        File soundFile = new File(config.appDataDir, "prevent-app-nap-silent-sound.aiff");
        if (!soundFile.exists()) {
            FileUtil.resourceToFile("prevent-app-nap-silent-sound.aiff", soundFile);
        }
        return soundFile;
    }

    private SourceDataLine getSourceDataLine(AudioFormat audioFormat) throws LineUnavailableException {
        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
        return (SourceDataLine) AudioSystem.getLine(dataLineInfo);
    }

    private Optional<String> inhibitorPath() {
        Optional<String> gnomeSessionInhibitorPathSpec = gnomeSessionInhibitPathSpec.get();
        return gnomeSessionInhibitorPathSpec.isPresent() ? gnomeSessionInhibitorPathSpec : systemdInhibitPathSpec.get();
    }

    private Optional<String[]> inhibitCommand() {
        if (inhibitorPathSpec.isPresent()) {
            String cmd = inhibitorPathSpec.get();
            final String[] params;
            if (cmd.contains("gnome-session-inhibit")) {
                params = new String[]{cmd, "--app-id", "Bisq", "--inhibit", "suspend", "--reason", "Avoid Standby", "--inhibit-only"};
            } else {
                // systemd-inhibit arguments:  must run a command;  there is no --inhibit-only option
                params = new String[]{cmd, "--who", "Bisq", "--what", "sleep", "--why", "Avoid Standby", "--mode", "block", "tail", "-f", "/dev/null"};
            }
            return Optional.of(params);
        } else {
            return Optional.empty();
        }
    }

    private final Predicate<String> isCommandInstalled = (p) -> {
        File executable = Paths.get(p).toFile();
        return executable.exists() && executable.canExecute();
    };

    private final Function<String[], Optional<String>> commandPath = (possiblePaths) -> {
        for (String path : possiblePaths) {
            if (isCommandInstalled.test(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    };

    private final Supplier<Optional<String>> gnomeSessionInhibitPathSpec = () ->
            commandPath.apply(new String[]{"/usr/bin/gnome-session-inhibit", "/bin/gnome-session-inhibit"});

    private final Supplier<Optional<String>> systemdInhibitPathSpec = () ->
            commandPath.apply(new String[]{"/usr/bin/systemd-inhibit", "/bin/systemd-inhibit"});
}
