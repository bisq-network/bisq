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

package io.bitsquare.gui.util;

import io.bitsquare.locale.Country;

import javafx.scene.image.*;

import com.sun.javafx.tk.quantum.QuantumToolkit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ImageUtil {
    private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

    // System tray use AWT and there is no CSS support for loading retina supported images
    public static final String SYS_TRAY = "/images/system_tray_icon.png";
    public static final String SYS_TRAY_HI_RES = "/images/system_tray_icon@2x.png";


    public static final String REMOVE_ICON = "image-remove";
    public static final String EXPAND = "image-expand";
    public static final String COLLAPSE = "image-collapse";

    public static ImageView getImageViewById(String id) {
        ImageView imageView = new ImageView();
        imageView.setId(id);
        return imageView;
    }

    private static Image getImageByUrl(String url) {
        return new Image(ImageUtil.class.getResourceAsStream(url));
    }

    public static ImageView getImageViewByUrl(String url) {
        return new ImageView(getImageByUrl(url));
    }

    public static ImageView getCountryIconImageView(Country country) {
        try {
            return ImageUtil.getImageViewByUrl("/images/countries/" + country.getCode().toLowerCase() + ".png");
        } catch (Exception e) {
            log.error("Country icon not found URL = /images/countries/" + country.getCode().toLowerCase() +
                    ".png / country name = " + country.getName());
            return null;
        }
    }

    public static boolean isRetina() {
        return ((QuantumToolkit) QuantumToolkit.getToolkit()).getMaxPixelScale() > 1.9f;
    }

}
