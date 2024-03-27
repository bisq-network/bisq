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

package bisq.persistence;

import java.io.File;

public class RollingBackups {
    private final File baseFile;
    private final int numberOfBackups;
    private final File parentDirFile;
    private final String baseFileName;

    public RollingBackups(File baseFile, int numberOfBackups) {
        if (numberOfBackups < 1) {
            throw new IllegalArgumentException("Number of backup is " + numberOfBackups);
        }

        this.baseFile = baseFile;
        this.numberOfBackups = numberOfBackups;
        parentDirFile = baseFile.getParentFile();
        baseFileName = baseFile.getName();
    }

    public void rollBackups() {
        for (int i = numberOfBackups - 2; i >= 0; i--) {
            File originalFile = new File(parentDirFile, baseFileName + "_" + i);
            File backupFile = new File(parentDirFile, baseFileName + "_" + (i + 1));
            renameFile(originalFile, backupFile);
        }

        File backupFile = new File(parentDirFile, baseFileName + "_0");
        renameFile(baseFile, backupFile);
    }

    private void renameFile(File originalFile, File newFile) {
        if (!originalFile.exists()) {
            return;
        }

        if (newFile.exists()) {
            boolean isSuccess = newFile.delete();
            if (!isSuccess) {
                throw new RollingBackupCreationFailedException("Couldn't delete " + newFile.getAbsolutePath() +
                        " before replacing it.");
            }
        }

        boolean isSuccess = originalFile.renameTo(newFile);
        if (!isSuccess) {
            throw new RollingBackupCreationFailedException("Couldn't rename " + originalFile.getAbsolutePath() + " to "
                    + newFile.getAbsolutePath());
        }
    }
}
