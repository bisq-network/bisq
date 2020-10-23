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

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.common.util.Preconditions.checkDir;

@Slf4j
@Singleton
public class TorSetup {
    private final File torDir;

    @Inject
    public TorSetup(@Named(Config.TOR_DIR) File torDir) {
        this.torDir = checkDir(torDir);
    }

    // Should only be called if needed. Slows down Tor startup from about 5 sec. to 30 sec. if it gets deleted.
    public void cleanupTorFiles(@Nullable Runnable resultHandler, @Nullable ErrorMessageHandler errorMessageHandler) {
        File hiddenservice = new File(Paths.get(torDir.getAbsolutePath(), "hiddenservice").toString());
        try {
            FileUtil.deleteDirectory(torDir, hiddenservice, true);
            if (resultHandler != null)
                resultHandler.run();
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.toString());
            if (errorMessageHandler != null)
                errorMessageHandler.handleErrorMessage(e.toString());
        }
    }
}
