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

package io.bitsquare.gui;

import io.bitsquare.Bitsquare;
import io.bitsquare.BitsquareUI;
import io.bitsquare.gui.util.ImageUtil;

import java.awt.*;

import java.util.concurrent.TimeoutException;

import javax.swing.*;

import javafx.application.Platform;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import scala.concurrent.duration.Duration;

/**
 * There is no JavaFX support yet, so we need to use AWT.
 */
public class SystemTray {
    private static final Logger log = LoggerFactory.getLogger(SystemTray.class);

    private final Stage stage;
    private final ActorSystem actorSystem;
    private final BitsquareUI application;

    private boolean isStageVisible = true;
    private MenuItem showGuiItem;
    private TrayIcon trayIcon;

    public SystemTray(Stage stage, ActorSystem actorSystem, BitsquareUI application) {
        this.stage = stage;
        this.actorSystem = actorSystem;
        this.application = application;

        if (java.awt.SystemTray.isSupported()) {
            // prevent exiting the app when the last window get closed
            Platform.setImplicitExit(false);

            java.awt.SystemTray systemTray = java.awt.SystemTray.getSystemTray();
            if (ImageUtil.isRetina())
                trayIcon = new TrayIcon(getImage(ImageUtil.SYS_TRAY_HI_RES));
            else
                trayIcon = new TrayIcon(getImage(ImageUtil.SYS_TRAY));

            trayIcon.setToolTip("Bitsquare P2P Fiat-Bitcoin exchange");

            PopupMenu popupMenu = new PopupMenu();
            MenuItem aboutItem = new MenuItem("Info about " + Bitsquare.getAppName());
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
                }
                else {
                    showGuiItem.setLabel("Close exchange window");
                    Platform.runLater(stage::show);
                    isStageVisible = true;
                }
            });
            exitItem.addActionListener(e -> {
                systemTray.remove(trayIcon);
                actorSystem.shutdown();
                try {
                    actorSystem.awaitTermination(Duration.create(5L, "seconds"));
                } catch (Exception ex) {
                    if (ex instanceof TimeoutException)
                        log.error("ActorSystem did not shutdown properly.");
                    else
                        log.error(ex.getMessage());
                }
                try {
                    application.stop();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            });


            try {
                systemTray.add(trayIcon);
            } catch (AWTException e) {
                log.error("TrayIcon could not be added.");
            }
        }
        else {
            log.error("SystemTray is not supported");
        }
    }

    public void setStageHidden() {
        stage.hide();
        isStageVisible = false;
        showGuiItem.setLabel("Open exchange window");
    }

    private Image getImage(String path) {
        return new ImageIcon(SystemTray.class.getResource(path), "system tray icon").getImage();
    }
}
