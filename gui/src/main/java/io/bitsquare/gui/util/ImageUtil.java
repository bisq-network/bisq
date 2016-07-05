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

import com.sun.javafx.tk.quantum.QuantumToolkit;
import io.bitsquare.locale.Country;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ImageUtil {
    private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

    public static final String REMOVE_ICON = "image-remove";

    public static ImageView getImageViewById(String id) {
        ImageView imageView = new ImageView();
        imageView.setId(id);
        return imageView;
    }

    private static Image getImageByUrl(String url) {
        return new Image(ImageUtil.class.getResourceAsStream(url));
    }

    private static ImageView getImageViewByUrl(String url) {
        return new ImageView(getImageByUrl(url));
    }

    public static ImageView getCountryIconImageView(Country country) {
        try {
            return ImageUtil.getImageViewByUrl("/images/countries/" + country.code.toLowerCase() + ".png");
        } catch (Exception e) {
            log.error("Country icon not found URL = /images/countries/" + country.code.toLowerCase() +
                    ".png / country name = " + country.name);
            return null;
        }
    }

    public static boolean isRetina() {
        float maxRenderScale = ((QuantumToolkit) QuantumToolkit.getToolkit()).getMaxRenderScale();
        boolean isRetina = maxRenderScale > 1.9f;
        //log.info("isRetina=" + isRetina + " / maxRenderScale=" + maxRenderScale);
        return isRetina;
    }
}
