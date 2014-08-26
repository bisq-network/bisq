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

import javafx.scene.image.*;

public class ImageUtil {
    public static final String SPLASH_LOGO = "/images/logo_200_270.png";
    public static final String SPLASH_LABEL = "/images/bitsquare_logo_label_300_69.png";

    public static final String SYS_TRAY = "/images/systemTrayIcon.png";
    public static final String SYS_TRAY_ALERT = "/images/systemTrayAlertIcon.png";

    public static final String HOME = "/images/nav/home.png";
    public static final String HOME_ACTIVE = "/images/nav/home_active.png";
    public static final String NAV_BUY = "/images/nav/nav_buy.png";
    public static final String NAV_BUY_ACTIVE = "/images/nav/nav_buy_active.png";
    public static final String NAV_SELL = "/images/nav/nav_sell.png";
    public static final String NAV_SELL_ACTIVE = "/images/nav/nav_sell_active.png";
    public static final String ORDERS = "/images/nav/orders.png";
    public static final String ORDERS_ACTIVE = "/images/nav/orders_active.png";
    public static final String FUNDS = "/images/nav/funds.png";
    public static final String FUNDS_ACTIVE = "/images/nav/funds_active.png";
    public static final String MSG = "/images/nav/msg.png";
    public static final String MSG_ACTIVE = "/images/nav/msg_active.png";
    public static final String SETTINGS = "/images/nav/settings.png";
    public static final String SETTINGS_ACTIVE = "/images/nav/settings_active.png";

    public static final String MSG_ALERT = "/images/nav/alertRound.png";

    public static final String BUY = "/images/buy.png";
    public static final String SELL = "/images/sell.png";
    public static final String REMOVE = "/images/removeOffer.png";


    public static Image getIconImage(String iconName) {
        return new Image(ImageUtil.class.getResourceAsStream(iconName));
    }


    public static ImageView getIconImageView(String iconName) {
        return new ImageView(new Image(ImageUtil.class.getResourceAsStream(iconName)));
    }
}
