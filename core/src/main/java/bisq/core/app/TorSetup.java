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

import bisq.common.config.Config;
import bisq.common.file.FileUtil;
import bisq.common.handlers.ErrorMessageHandler;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.common.util.Preconditions.checkDir;

@Slf4j
@Singleton
public class TorSetup {
    // The onion service key and the user's configuration of an external Tor, which the Tor library reads from the
    // Tor directory.
    private static final Set<String> KEPT_FILE_NAMES = Set.of("hiddenservice", "external_tor.config");

    private final File torDir;

    @Inject
    public TorSetup(@Named(Config.TOR_DIR) File torDir) {
        this.torDir = checkDir(torDir);
    }

    // Should only be called if needed. Slows down Tor startup from about 5 sec. to 30 sec. if it gets deleted.
    public void cleanupTorFiles(@Nullable Runnable resultHandler, @Nullable ErrorMessageHandler errorMessageHandler) {
        try {
            File[] files = torDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!KEPT_FILE_NAMES.contains(file.getName())) {
                        FileUtil.deleteDirectory(file, null, true);
                    }
                }
            }
            log.info("All Tor files except the hiddenservice directory and external_tor.config got deleted");
            if (resultHandler != null)
                resultHandler.run();
        } catch (IOException e) {
            log.error("cleanupTorFiles failed", e);
            if (errorMessageHandler != null)
                errorMessageHandler.handleErrorMessage(e.toString());
        }
    }
}
