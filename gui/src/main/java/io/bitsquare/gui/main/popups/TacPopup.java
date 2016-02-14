package io.bitsquare.gui.main.popups;

import com.google.inject.Inject;
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.common.UserThread;
import io.bitsquare.user.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class TacPopup extends Popup {
    private static final Logger log = LoggerFactory.getLogger(TacPopup.class);
    private Preferences preferences;

    @Inject
    public TacPopup(Preferences preferences) {
        this.preferences = preferences;
    }

    public void showIfNeeded() {
        if (!preferences.getTacAccepted() && !BitsquareApp.DEV_MODE) {
            // TODO add link: https://bitsquare.io/arbitration_system.pdf
            headLine("USER AGREEMENT");
            String text = "1. This software is experimental and provided \"as is\", without warranty of any kind, " +
                    "express or implied, including but not limited to the warranties of " +
                    "merchantability, fitness for a particular purpose and non-infringement.\n" +
                    "In no event shall the authors or copyright holders be liable for any claim, damages or other " +
                    "liability, whether in an action of contract, tort or otherwise, " +
                    "arising from, out of or in connection with the software or the use or other dealings in the software.\n\n" +
                    "2. The user is responsible to use the software in compliance with local laws.\n\n" +
                    "3. The user confirms that he has read and agreed to the rules defined in our " +
                    "Wiki regrading the dispute process\n" +
                    "(https://github.com/bitsquare/bitsquare/wiki/Arbitration-system).";
            message(text);
            actionButtonText("I agree");
            closeButtonText("I disagree and quit");
            onAction(() -> {
                preferences.setTacAccepted(true);
                if (preferences.getBitcoinNetwork() == BitcoinNetwork.MAINNET)
                    UserThread.runAfter(() -> new Popup()
                            .warning("This software is still in alpha version.\n" +
                                    "Please be aware that using Mainnet comes with the risk to lose funds " +
                                    "in case of software bugs.\n" +
                                    "To limit the possible losses the maximum allowed trading amount and the " +
                                    "security deposit have been reduced to 0.01 BTC for the alpha version " +
                                    "when using Mainnet.")
                            .headLine("Important information!")
                            .actionButtonText("I understand and want to use Mainnet")
                            .closeButtonText("Restart and use Testnet")
                            .onClose(() -> {
                                UserThread.execute(() -> preferences.setBitcoinNetwork(BitcoinNetwork.TESTNET));
                                UserThread.runAfter(BitsquareApp.shutDownHandler::run, 300, TimeUnit.MILLISECONDS);
                            })
                            .width(600)
                            .show(), 300, TimeUnit.MILLISECONDS);
            });
            onClose(BitsquareApp.shutDownHandler::run);
            super.show();
        }
    }
}
