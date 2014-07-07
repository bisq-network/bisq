package io.bitsquare.util;


import io.bitsquare.BitSquare;
import java.awt.*;
import javafx.application.Platform;
import javafx.stage.Stage;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AWTSystemTray
{
    private static final Logger log = LoggerFactory.getLogger(AWTSystemTray.class);
    private static boolean isStageVisible = true;
    private static MenuItem showGuiItem;
    private static Stage stage;

    public static void createSystemTray(Stage stage)
    {
        AWTSystemTray.stage = stage;
        if (SystemTray.isSupported())
        {
            // prevent exiting the app when the last window get closed
            Platform.setImplicitExit(false);

            SystemTray systemTray = SystemTray.getSystemTray();
            TrayIcon trayIcon = new TrayIcon(getImage());
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

            trayIcon.addActionListener(e -> JOptionPane.showMessageDialog(null, "This dialog box is run from System Tray"));

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
                Platform.exit();
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

    public static void setStageHidden()
    {
        stage.hide();
        isStageVisible = false;
        showGuiItem.setLabel("Open exchange window");
    }

    private static Image getImage()
    {
        return new ImageIcon(AWTSystemTray.class.getResource("/images/systemTrayIcon.png"), "system tray icon").getImage();
    }
}
