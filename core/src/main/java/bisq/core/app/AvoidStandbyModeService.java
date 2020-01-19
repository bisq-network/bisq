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

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;

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

    private volatile boolean isStopped;

    @Inject
    public AvoidStandbyModeService(Preferences preferences, Config config) {
        this.preferences = preferences;
        this.config = config;

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
        new Thread(this::play, "AvoidStandbyModeService-thread").start();
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
}
