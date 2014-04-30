package io.bitsquare.gui.components;

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.util.FormBuilder;
import io.bitsquare.gui.util.Icons;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class ConfirmationComponent implements WalletFacade.WalletListener
{

    private static final Logger log = LoggerFactory.getLogger(ConfirmationComponent.class);
    private ImageView confirmIconImageView;
    private TextField confirmationsLabel;
    private ProgressIndicator confirmSpinner;

    public ConfirmationComponent(WalletFacade walletFacade, GridPane gridPane, int row)
    {
        confirmationsLabel = FormBuilder.addConfirmationsLabel(gridPane, walletFacade, row);
        confirmIconImageView = FormBuilder.addConfirmationsIcon(gridPane, walletFacade, row);
        confirmSpinner = FormBuilder.addConfirmationsSpinner(gridPane, walletFacade, row);
    }

    @Override
    public void onConfidenceChanged(int numBroadcastPeers, int depthInBlocks)
    {
        confirmIconImageView.setImage(getConfirmIconImage(numBroadcastPeers, depthInBlocks));
        confirmationsLabel.setText(getConfirmationsText(numBroadcastPeers, depthInBlocks));
        if (depthInBlocks == 0)
            confirmSpinner.setProgress(-1);
        else
            confirmSpinner.setOpacity(0);

        log.info("onConfidenceChanged " + numBroadcastPeers + " / " + depthInBlocks);
    }

    @Override
    public void onCoinsReceived(BigInteger newBalance)
    {
        log.info("onCoinsReceived " + newBalance);
    }


    private String getConfirmationsText(int numBroadcastPeers, int depthInBlocks)
    {
        depthInBlocks = 0;
        return depthInBlocks + " confirmation(s) / " + "Seen by " + numBroadcastPeers + " peer(s)";
    }

    private Image getConfirmIconImage(int numBroadcastPeers, int depthInBlocks)
    {
        depthInBlocks = 0;
        if (depthInBlocks > 0)
            return Icons.getIconImage(Icons.getIconIDForConfirmations(depthInBlocks));
        else
            return Icons.getIconImage(Icons.getIconIDForPeersSeenTx(numBroadcastPeers));
    }


}
