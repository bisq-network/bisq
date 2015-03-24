/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.persistence;

import io.bitsquare.gui.components.Popups;
import io.bitsquare.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Storage<T extends Serializable> {
    private static final Logger log = LoggerFactory.getLogger(Storage.class);

    public static final String DIR_KEY = "storage.dir";
    private final File dir;
    private File storageFile;
    private T serializable;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Storage(@Named(DIR_KEY) File dir) {
        this.dir = dir;
    }

    public void save() {
        if (storageFile == null)
            throw new RuntimeException("storageFile = null. Call setupFileStorage before using read/write.");

        try {
            FileUtil.write(serializable, dir, storageFile);
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            Popups.openErrorPopup("An exception occurred at writing data to disc.", e.getMessage());
        }
    }

    public T getPersisted(T serializable) {
        this.serializable = serializable;
        storageFile = new File(dir, serializable.getClass().getSimpleName() + ".ser");
        
        if (storageFile == null)
            throw new RuntimeException("storageFile = null. Call init before using read/write.");

        try {
            return (T) FileUtil.read(storageFile);
        } catch (FileNotFoundException e) {
            log.info("File not available. That is OK for the first run.");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            Popups.openErrorPopup("An exception occurred at reading data from disc.", e.getMessage());

        }
        return null;
    }
}
