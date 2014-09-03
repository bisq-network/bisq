package io.bitsquare.gui.help;

import io.bitsquare.BitSquare;

import java.net.MalformedURLException;
import java.net.URL;

import javafx.scene.*;
import javafx.scene.web.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO Find good solution for a web based help content management system. 
public class Help {
    private static final Logger log = LoggerFactory.getLogger(Help.class);

    private static Stage helpWindow;
    

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void openWindow(HelpId id) {
        try {
            URL url = new URL("https://github.com/bitsquare/bitsquare/wiki/?" + id);
            WebView webView;
            if (helpWindow == null) {
                helpWindow = new Stage();
                helpWindow.initModality(Modality.NONE);
                helpWindow.initOwner(BitSquare.getPrimaryStage());
                webView = new WebView();
                helpWindow.setScene(new Scene(webView, 800, 600));
            }
            else {
                webView = (WebView) helpWindow.getScene().getRoot();
            }
            helpWindow.setTitle(url.toString());
            webView.getEngine().load(url.toString());
            helpWindow.show();
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
        }
    }

}
