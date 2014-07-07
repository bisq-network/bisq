package io.bitsquare.gui.components.btc;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;

public class AddressTextField extends AnchorPane
{
    private final Label copyIcon;
    private final TextField addressTextField;
    private String address;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AddressTextField()
    {
        addressTextField = new TextField();
        addressTextField.setEditable(false);

        copyIcon = new Label();
        copyIcon.setLayoutY(3);
        copyIcon.setOnMouseClicked(e -> {
            if (address != null && address.length() > 0)
            {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(address);
                clipboard.setContent(content);
            }
        });

        Tooltip copyIconTooltip = new Tooltip("Copy address to clipboard");
        Tooltip.install(copyIcon, copyIconTooltip);


        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        copyIcon.setId("copy-icon");

        AnchorPane.setRightAnchor(copyIcon, 5.0);
        AnchorPane.setRightAnchor(addressTextField, 35.0);
        AnchorPane.setLeftAnchor(addressTextField, 0.0);

        getChildren().addAll(addressTextField, copyIcon);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void setAddress(String address)
    {
        this.address = address;
        addressTextField.setText(address);
    }

}
