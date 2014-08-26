/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.util;


import io.bitsquare.BitSquare;
import io.bitsquare.gui.util.ImageUtil;

import java.awt.*;

import javafx.application.Platform;
import javafx.stage.Stage;

import javax.swing.ImageIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AWTSystemTray {
    private static final Logger log = LoggerFactory.getLogger(AWTSystemTray.class);
    private static boolean isStageVisible = true;
    private static MenuItem showGuiItem;
    private static Stage stage;
    private static TrayIcon trayIcon;

    public static void createSystemTray(Stage stage) {
        AWTSystemTray.stage = stage;
        if (SystemTray.isSupported()) {
            // prevent exiting the app when the last window get closed
            Platform.setImplicitExit(false);

            SystemTray systemTray = SystemTray.getSystemTray();
            trayIcon = new TrayIcon(getImage(ImageUtil.SYS_TRAY));
            trayIcon.setToolTip("BitSquare P2P Fiat-Bitcoin exchange");

            PopupMenu popupMenu = new PopupMenu();
            MenuItem aboutItem = new MenuItem("Info about " + BitSquare.getAppName());
            popupMenu.add(aboutItem);
            popupMenu.addSeparator();
            showGuiItem = new MenuItem("Close exchange window");
            popupMenu.add(showGuiItem);
            popupMenu.addSeparator();
            MenuItem exitItem = new MenuItem("Exit");
            popupMenu.add(exitItem);

            trayIcon.setPopupMenu(popupMenu);

            showGuiItem.addActionListener(e -> {
                if (isStageVisible) {
                    showGuiItem.setLabel("Open exchange window");
                    Platform.runLater(stage::hide);
                    isStageVisible = false;
                } else {
                    showGuiItem.setLabel("Close exchange window");
                    Platform.runLater(stage::show);
                    isStageVisible = true;
                }
            });
            exitItem.addActionListener(e -> {
                systemTray.remove(trayIcon);
                System.exit(0);
            });


            try {
                systemTray.add(trayIcon);
            } catch (AWTException e) {
                log.error("TrayIcon could not be added.");
            }
        } else {
            log.error("SystemTray is not supported");
        }
    }

    public static void setAlert() {
        trayIcon.setImage(getImage(ImageUtil.SYS_TRAY_ALERT));
    }

    public static void unSetAlert() {
        trayIcon.setImage(getImage(ImageUtil.SYS_TRAY));
    }

    public static void setStageHidden() {
        stage.hide();
        isStageVisible = false;
        showGuiItem.setLabel("Open exchange window");
    }

    private static Image getImage(String path) {
        return new ImageIcon(AWTSystemTray.class.getResource(path), "system tray icon").getImage();
    }
}
