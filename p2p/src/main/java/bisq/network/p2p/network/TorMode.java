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

package bisq.network.p2p.network;

import bisq.network.tor.TorService;
import bisq.network.tor.onionservice.OnionServiceKey;

import bisq.common.file.FileUtil;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import java.io.File;
import java.io.IOException;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * Holds information on how tor should be created and delivers a respective
 * {@link TorService} object when asked.
 *
 * @author Florian Reimair
 *
 */
@Slf4j
public abstract class TorMode {

    /**
     * The sub-directory where the <code>private_key</code> file sits in. netlayer
     * kept the key of the onion service there, so we use it as well.
     */
    private static final String HIDDEN_SERVICE_DIRECTORY = "hiddenservice";
    // The key of the onion service and the user's settings of a running Tor, which the Tor library reads from the Tor
    // directory
    private static final Set<String> KEPT_FILE_NAMES = Set.of(HIDDEN_SERVICE_DIRECTORY, "external_tor.config");

    protected final File torDir;

    /**
     * @param torDir           points to the place, where we will persist private
     *                         key and address data
     */
    public TorMode(File torDir) {
        this.torDir = torDir;
    }

    /**
     * Starts Tor and returns the {@link TorService} which controls it. Blocks until
     * Tor has bootstrapped.
     *
     * @return a started {@link TorService}
     */
    public abstract TorService getTor();

    /**
     * Returns the key of the onion service from the hidden service directory, and
     * creates it at the first start.
     *
     * @return the key of the onion service
     * @throws IOException if the key files cannot be read or written
     */
    public OnionServiceKey getOnionServiceKey() throws IOException {
        return OnionServiceKeyStorage.loadOrCreate(new File(torDir, HIDDEN_SERVICE_DIRECTORY).toPath());
    }

    /**
     * Do a rolling backup of the "private_key" file.
     */
    protected void doRollingBackup() {
        FileUtil.rollingBackup(new File(torDir, HIDDEN_SERVICE_DIRECTORY), "private_key", 20);
    }

    /**
     * Deletes the files of Tor in the Tor directory, except the key of the onion
     * service and <code>external_tor.config</code>. Tor creates them again at the
     * next start.
     *
     * @param torDir the Tor directory
     * @throws IOException if a file cannot be deleted
     */
    public static void deleteTorFiles(File torDir) throws IOException {
        File[] files = torDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!KEPT_FILE_NAMES.contains(file.getName())) {
                    deleteWithoutFollowingLinks(file.toPath());
                }
            }
        }
    }

    // A symbolic link is deleted itself, so that no file outside the Tor directory is deleted. As before, a file which
    // cannot be deleted, for example one which a running Tor keeps open on Windows, is skipped.
    private static void deleteWithoutFollowingLinks(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (attributes.isOther()) {
                    // A junction of Windows, which Files.walkFileTree reports as a directory and would enter. Tor
                    // may write its data directory, so it could point a junction there to another directory.
                    deleteOrLog(directory);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                deleteOrLog(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                log.info("Could not read {}: {}", file, e.toString());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException e) {
                deleteOrLog(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteOrLog(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.info("Could not delete {}: {}", path, e.toString());
        }
    }

}
