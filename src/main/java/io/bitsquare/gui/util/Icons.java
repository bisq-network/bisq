package io.bitsquare.gui.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

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
    public static final String HISTORY = "/images/nav/history.png";
    public static final String HISTORY_ACTIVE = "/images/nav/history_active.png";
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

    public static final String PROGRESS_0_ICON_FILE = "/images/tx/circleProgress0.png";
    public static final String PROGRESS_1_ICON_FILE = "/images/tx/circleProgress1.png";
    public static final String PROGRESS_2_ICON_FILE = "/images/tx/circleProgress2.png";
    public static final String PROGRESS_3_ICON_FILE = "/images/tx/circleProgress3.png";
    public static final String PROGRESS_4_ICON_FILE = "/images/tx/circleProgress4.png";
    public static final String PROGRESS_5_ICON_FILE = "/images/tx/circleProgress5.png";
    public static final String FULL_CONFIRMED = "/images/tx/fullConfirmed.png";

    public static final String SHAPE_TRIANGLE_ICON_FILE = "/images/tx/shapeTriangle.png";
    public static final String SHAPE_SQUARE_ICON_FILE = "/images/tx/shapeSquare.png";
    public static final String SHAPE_PENTAGON_ICON_FILE = "/images/tx/shapePentagon.png";
    public static final String SHAPE_HEXAGON_ICON_FILE = "/images/tx/shapeHexagon.png";


    public static Image getIconImage(String iconName)
    {
        return new Image(Icons.class.getResourceAsStream(iconName));
    }

    public static ImageView getIconImageView(String iconName)
    {
        return new ImageView(new Image(Icons.class.getResourceAsStream(iconName)));
    }

    public static String getIconIDForConfirmations(int confirmations)
    {
        switch (confirmations)
        {
            case 0:
                return Icons.PROGRESS_0_ICON_FILE;
            case 1:
                return Icons.PROGRESS_1_ICON_FILE;
            case 2:
                return Icons.PROGRESS_2_ICON_FILE;
            case 3:
                return Icons.PROGRESS_3_ICON_FILE;
            case 4:
                return Icons.PROGRESS_4_ICON_FILE;
            case 5:
                return Icons.PROGRESS_5_ICON_FILE;
            case 6:
            default:
                return Icons.FULL_CONFIRMED;
        }
    }

    public static String getIconIDForPeersSeenTx(int numberOfPeersSeenTx)
    {
        switch (numberOfPeersSeenTx)
        {
            case 0:
                return Icons.PROGRESS_0_ICON_FILE;
            case 1:
                return Icons.SHAPE_TRIANGLE_ICON_FILE;
            case 2:
                return Icons.SHAPE_SQUARE_ICON_FILE;
            case 3:
                return Icons.SHAPE_PENTAGON_ICON_FILE;
            case 4:
            default:
                return Icons.SHAPE_HEXAGON_ICON_FILE;
        }
    }
}
