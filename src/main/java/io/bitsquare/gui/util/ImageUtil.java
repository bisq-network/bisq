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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageUtil {
    private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

    public static final String SPLASH_LOGO = "/images/logo_splash.png";

    public static final String SYS_TRAY = "/images/system_tray_icon_44_32.png";
    public static final String SYS_TRAY_ALERT = "/images/system_tray_notify_icon_44_32.png";

    public static final String HOME = "/images/nav/home.png";
    public static final String HOME_ACTIVE = "/images/nav/home_active.png";
    public static final String BUY = "/images/nav/buy.png";
    public static final String BUY_ACTIVE = "/images/nav/buy_active.png";
    public static final String SELL = "/images/nav/sell.png";
    public static final String SELL_ACTIVE = "/images/nav/sell_active.png";
    public static final String ORDERS = "/images/nav/orders.png";
    public static final String ORDERS_ACTIVE = "/images/nav/orders_active.png";
    public static final String FUNDS = "/images/nav/funds.png";
    public static final String FUNDS_ACTIVE = "/images/nav/funds_active.png";
    public static final String MSG = "/images/nav/msg.png";
    public static final String MSG_ACTIVE = "/images/nav/msg_active.png";
    public static final String SETTINGS = "/images/nav/settings.png";
    public static final String SETTINGS_ACTIVE = "/images/nav/settings_active.png";
    public static final String ACCOUNT = "/images/nav/account.png";
    public static final String ACCOUNT_ACTIVE = "/images/nav/account_active.png";

    public static final String MSG_ALERT = "/images/nav/alertRound.png";

    public static final String BUY_ICON = "/images/buy.png";
    public static final String SELL_ICON = "/images/sell.png";
    public static final String REMOVE_ICON = "/images/removeOffer.png";

    public static final String EXPAND = "/images/expand.png";
    public static final String COLLAPSE = "/images/collapse.png";

    public static final String TICK = "/images/tick.png";
    public static final String ARROW_BLUE = "/images/arrow_blue.png";
    public static final String ARROW_GREY = "/images/arrow_grey.png";
    public static final String INFO = "/images/info.png";

    public static Image getIconImage(String iconName) {
        return new Image(ImageUtil.class.getResourceAsStream(iconName));
    }

    public static ImageView getIconImageView(String iconName) {
        return new ImageView(new Image(ImageUtil.class.getResourceAsStream(iconName)));
    }

    public static ImageView getCountryIconImageView(Country country) {
        try {
            return ImageUtil.getIconImageView("/images/countries/" + country.getCode().toLowerCase() + ".png");

        } catch (Exception e) {
            log.error("Country icon not found URL = /images/countries/" + country.getCode().toLowerCase() +
                    ".png / country name = " + country.getName());
            return null;
        }
    }


}
