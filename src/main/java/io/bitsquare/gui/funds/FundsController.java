package io.bitsquare.gui.funds;

import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.util.ConfidenceDisplay;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;


public class FundsController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(FundsController.class);
    private WalletFacade walletFacade;
    private ConfidenceDisplay confidenceDisplay;

    @FXML
    private TextField tradingAccountTextField, balanceTextField;
    @FXML
    private Label copyIcon, confirmationLabel;
    @FXML
    private ProgressIndicator progressIndicator;

    @Inject
    public FundsController(WalletFacade walletFacade)
    {
        this.walletFacade = walletFacade;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        String tradingAccountAddress = walletFacade.getAddressAsString();
        tradingAccountTextField.setText(tradingAccountAddress);

        copyIcon.setId("copy-icon");
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
        copyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(tradingAccountAddress);
            clipboard.setContent(content);
        });

        confidenceDisplay = new ConfidenceDisplay(walletFacade.getWallet(), confirmationLabel, balanceTextField, progressIndicator);
    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
    }

    @Override
    public void cleanup()
    {

    }
}

