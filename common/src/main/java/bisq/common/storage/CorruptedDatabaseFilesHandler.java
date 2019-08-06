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

package bisq.common.storage;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CorruptedDatabaseFilesHandler {
    private List<String> corruptedDatabaseFiles = new ArrayList<>();

    @Inject
    public CorruptedDatabaseFilesHandler() {
    }

    public void onFileCorrupted(String fileName) {
        corruptedDatabaseFiles.add(fileName);
    }

    public Optional<List<String>> getCorruptedDatabaseFiles() {
        if (!corruptedDatabaseFiles.isEmpty()) {
            if (corruptedDatabaseFiles.size() == 1 && corruptedDatabaseFiles.get(0).equals("ViewPathAsString")) {
                log.debug("We detected incompatible data base file for Navigation. " +
                        "That is a minor issue happening with refactoring of UI classes " +
                        "and we don't display a warning popup to the user.");
                return Optional.empty();
            } else {
                return Optional.of(corruptedDatabaseFiles);
            }
        } else {
            return Optional.empty();
        }
    }
}
