package io.bitsquare.gui.components;

import javafx.event.ActionEvent;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class BuySellSwitch extends ToggleButton
{

    private static Image buyIcon = new Image(BuySellSwitch.class.getResourceAsStream("/images/buy.png"));
    private static Image sellIcon = new Image(BuySellSwitch.class.getResourceAsStream("/images/sell.png"));

    public BuySellSwitch(String label)
    {
        super(label);

        ImageView iconImageView = new ImageView(buyIcon);
        //setClip(iconImageView);
        setGraphic(iconImageView);
        addEventHandler(ActionEvent.ACTION, e -> {
            if (isSelected())
            {

                setText("SELL");
                iconImageView.setImage(sellIcon);
            }
            else
            {
                setText("BUY");
                iconImageView.setImage(buyIcon);
            }
        });
    }


}
