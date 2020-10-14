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
import bisq.common.file.FileUtil;
import bisq.common.file.ResourceNotFoundException;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
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

/**
 * Prevents that Bisq gets hibernated from the OS. On OSX there is a tool called caffeinate but it seems it does not
 * provide the behaviour we need, thus we use the trick to play a almost silent sound file in a loop. This keeps the
 * application active even if the OS has moved to hibernate. Hibernating Bisq would cause network degradations and other
 * resource limitations which would lead to offers not published or if a taker takes an offer that the trade process is
 * at risk to fail due too slow response time.
 */
@Slf4j
@Singleton
public class AvoidStandbyModeService {

    private final Preferences preferences;
    private final Config config;
    private final Optional<String> inhibitorPathSpec;
    private CountDownLatch stopLinuxInhibitorCountdownLatch;

    private volatile boolean isStopped;

    @Inject
    public AvoidStandbyModeService(Preferences preferences, Config config) {
        this.preferences = preferences;
        this.config = config;
        this.inhibitorPathSpec = inhibitorPath();
        preferences.getUseStandbyModeProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                if (Utilities.isLinux() && runningInhibitorProcess().isPresent()) {
                    Objects.requireNonNull(stopLinuxInhibitorCountdownLatch).countDown();
                }
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
        if (Utilities.isLinux()) {
            startInhibitor();
        } else {
            new Thread(this::playSilentAudioFile, "AvoidStandbyModeService-thread").start();
        }
    }

    public void shutDown() {
        isStopped = true;
        stopInhibitor();
    }

    private void startInhibitor() {
        try {
            if (runningInhibitorProcess().isPresent()) {
                log.info("Inhibitor already started");
                return;
            }
            inhibitCommand().ifPresent(cmd -> {
                try {
                    new ProcessBuilder(cmd).start();
                    log.info("Started -- disabled power management via {}", String.join(" ", cmd));
                    if (Utilities.isLinux()) {
                        stopLinuxInhibitorCountdownLatch = new CountDownLatch(1);
                        new Thread(this::stopInhibitor, "StopAvoidStandbyModeService-thread").start();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            log.error("Cannot avoid standby mode", e);
        }
    }

    private void stopInhibitor() {
        try {
            if (Utilities.isLinux()) {
                if (!isStopped) {
                    Objects.requireNonNull(stopLinuxInhibitorCountdownLatch).await();
                }
                Optional<ProcessHandle> runningInhibitor = runningInhibitorProcess();
                runningInhibitor.ifPresent(processHandle -> {
                    processHandle.destroy();
                    log.info("Stopped");
                });
            }
        } catch (Exception e) {
            log.error("Stop inhibitor thread interrupted", e);
        }
    }

    private void playSilentAudioFile() {
        try {
            log.info("Started");
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
        // We replaced the old file which was 42 MB with a smaller file of 0.8 MB. To enforce replacement we check for
        // the size...
        if (!soundFile.exists() || soundFile.length() > 42000000) {
            FileUtil.resourceToFile("prevent-app-nap-silent-sound.aiff", soundFile);
        }
        return soundFile;
    }

    private SourceDataLine getSourceDataLine(AudioFormat audioFormat) throws LineUnavailableException {
        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
        return (SourceDataLine) AudioSystem.getLine(dataLineInfo);
    }

    private Optional<String> inhibitorPath() {
        for (Optional<String> installedInhibitor : installedInhibitors.get()) {
            if (installedInhibitor.isPresent()) {
                return installedInhibitor;
            }
        }
        return Optional.empty(); // falling back to silent audio file player
    }

    private Optional<String[]> inhibitCommand() {
        final String[] params;
        if (inhibitorPathSpec.isPresent()) {
            String cmd = inhibitorPathSpec.get();
            if (Utilities.isLinux()) {
                params = cmd.contains("gnome-session-inhibit")
                        ? new String[]{cmd, "--app-id", "Bisq", "--inhibit", "suspend", "--reason", "Avoid Standby", "--inhibit-only"}
                        : new String[]{cmd, "--who", "Bisq", "--what", "sleep", "--why", "Avoid Standby", "--mode", "block", "tail", "-f", "/dev/null"};
            } else {
                params = null;
            }
        } else {
            params = null; // fall back to silent audio file player
        }
        return params == null ? Optional.empty() : Optional.of(params);
    }

    private Optional<ProcessHandle> runningInhibitorProcess() {
        final ProcessHandle[] inhibitorProc = new ProcessHandle[1];
        inhibitorPathSpec.ifPresent(cmd -> {
            Optional<ProcessHandle> jvmProc = ProcessHandle.of(ProcessHandle.current().pid());
            jvmProc.ifPresent(proc -> proc.children().forEach(childProc -> childProc.info().command().ifPresent(command -> {
                if (command.equals(cmd) && childProc.isAlive()) {
                    inhibitorProc[0] = childProc;
                }
            })));
        });
        return inhibitorProc[0] == null ? Optional.empty() : Optional.of(inhibitorProc[0]);
    }

    private final Predicate<String> isCmdInstalled = (p) -> {
        File executable = Paths.get(p).toFile();
        return executable.exists() && executable.canExecute();
    };

    private final Function<String[], Optional<String>> cmdPath = (possiblePaths) -> {
        for (String path : possiblePaths) {
            if (isCmdInstalled.test(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    };

    private final Supplier<List<Optional<String>>> installedInhibitors = () ->
            new ArrayList<>() {{
                add(gnomeSessionInhibitPathSpec.get()); // On linux, preferred inhibitor is gnome-session-inhibit,
                add(systemdInhibitPathSpec.get());      // then fall back to systemd-inhibit if it is installed.
            }};

    private final Supplier<Optional<String>> gnomeSessionInhibitPathSpec = () ->
            cmdPath.apply(new String[]{"/usr/bin/gnome-session-inhibit", "/bin/gnome-session-inhibit"});

    private final Supplier<Optional<String>> systemdInhibitPathSpec = () ->
            cmdPath.apply(new String[]{"/usr/bin/systemd-inhibit", "/bin/systemd-inhibit"});
}
