package io.bitsquare.gui.funds;

import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.IWalletFacade;
import io.bitsquare.gui.IChildController;
import io.bitsquare.gui.INavigationController;
import io.bitsquare.gui.util.Formatter;
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


public class FundsController implements Initializable, IChildController
{

    private INavigationController navigationController;
    private IWalletFacade walletFacade;

    @FXML
    public Pane rootContainer;
    @FXML
    public TextField addressLabel;
    @FXML
    public TextField balanceLabel;
    @FXML
    public Label copyIcon;

    @Inject
    public FundsController(IWalletFacade walletFacade)
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
    public void setNavigationController(INavigationController navigationController)
    {
        this.navigationController = navigationController;

        addressLabel.setText(walletFacade.getAddress());
        balanceLabel.setText(Formatter.formatSatoshis(walletFacade.getBalance(), false));
    }



}

