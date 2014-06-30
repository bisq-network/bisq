package io.bitsquare.gui.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.jetbrains.annotations.NotNull;

public class Icons
{
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

    @NotNull
    public static Image getIconImage(String iconName)
    {
        return new Image(Icons.class.getResourceAsStream(iconName));
    }

    @NotNull
    public static ImageView getIconImageView(String iconName)
    {
        return new ImageView(new Image(Icons.class.getResourceAsStream(iconName)));
    }
}
