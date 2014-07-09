package io.bitsquare.util;


import io.bitsquare.BitSquare;
import io.bitsquare.gui.util.Icons;
import java.awt.*;
import javafx.application.Platform;
import javafx.stage.Stage;
import javax.swing.ImageIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AWTSystemTray
{
    private static final Logger log = LoggerFactory.getLogger(AWTSystemTray.class);
    private static boolean isStageVisible = true;
    private static MenuItem showGuiItem;
    private static Stage stage;
    private static TrayIcon trayIcon;

    public static void createSystemTray(Stage stage)
    {
        AWTSystemTray.stage = stage;
        if (SystemTray.isSupported())
        {
            // prevent exiting the app when the last window get closed
            Platform.setImplicitExit(false);

            SystemTray systemTray = SystemTray.getSystemTray();
            trayIcon = new TrayIcon(getImage(Icons.SYS_TRAY));
            trayIcon.setToolTip("BitSquare P2P Fiat-Bitcoin exchange");

            PopupMenu popupMenu = new PopupMenu();
            MenuItem aboutItem = new MenuItem("Info about " + BitSquare.getUID());
            popupMenu.add(aboutItem);
            popupMenu.addSeparator();
            showGuiItem = new MenuItem("Close exchange window");
            popupMenu.add(showGuiItem);
            popupMenu.addSeparator();
            MenuItem exitItem = new MenuItem("Exit");
            popupMenu.add(exitItem);

            trayIcon.setPopupMenu(popupMenu);

            showGuiItem.addActionListener(e -> {
                if (isStageVisible)
                {
                    showGuiItem.setLabel("Open exchange window");
                    Platform.runLater(stage::hide);
                    isStageVisible = false;
                }
                else
                {
                    showGuiItem.setLabel("Close exchange window");
                    Platform.runLater(stage::show);
                    isStageVisible = true;
                }
            });
            exitItem.addActionListener(e -> {
                systemTray.remove(trayIcon);
                System.exit(0);
            });


            try
            {
                systemTray.add(trayIcon);
            } catch (AWTException e)
            {
                log.error("TrayIcon could not be added.");
            }
        }
        else
        {
            log.error("SystemTray is not supported");
        }
    }

    public static void setAlert()
    {
        trayIcon.setImage(getImage(Icons.SYS_TRAY_ALERT));
    }

    public static void unSetAlert()
    {
        trayIcon.setImage(getImage(Icons.SYS_TRAY));
    }

    public static void setStageHidden()
    {
        stage.hide();
        isStageVisible = false;
        showGuiItem.setLabel("Open exchange window");
    }

    private static Image getImage(String path)
    {
        return new ImageIcon(AWTSystemTray.class.getResource(path), "system tray icon").getImage();
    }
}
