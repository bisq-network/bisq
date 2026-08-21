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

package bisq.common.file;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CorruptedStorageFileHandler {
    private final List<String> files = new ArrayList<>();

    @Inject
    public CorruptedStorageFileHandler() {
    }

    public void addFile(String fileName) {
        files.add(fileName);
    }

    public Optional<List<String>> getFiles() {
        if (files.isEmpty()) {
            return Optional.empty();
        }

        if (files.size() == 1 && files.get(0).equals("ViewPathAsString")) {
            log.debug("We detected incompatible data base file for Navigation. " +
                    "That is a minor issue happening with refactoring of UI classes " +
                    "and we don't display a warning popup to the user.");
            return Optional.empty();
        }

        return Optional.of(files);
    }
}
