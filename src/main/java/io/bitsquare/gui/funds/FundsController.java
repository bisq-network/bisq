package io.bitsquare.gui.funds;

import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.util.ResourceBundle;


public class FundsController implements Initializable, ChildController
{

    private NavigationController navigationController;
    private WalletFacade walletFacade;

    @FXML
    public Pane rootContainer;
    @FXML
    public TextField addressLabel;
    @FXML
    public TextField balanceLabel;
    @FXML
    public Label copyIcon;

    @Inject
    public FundsController(WalletFacade walletFacade)
    {
        this.walletFacade = walletFacade;


    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));

        copyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(addressLabel.getText());
            clipboard.setContent(content);
        });
    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;

        addressLabel.setText(walletFacade.getAddress());
        balanceLabel.setText(BtcFormatter.formatSatoshis(walletFacade.getBalance(), false));
    }


}

