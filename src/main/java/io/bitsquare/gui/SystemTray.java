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

    private static final String ICON_HI_RES = "/images/system_tray_icon@2x.png";
    private static final String ICON_LO_RES = "/images/system_tray_icon.png";

    public static final String SHOW_WINDOW_LABEL = "Show exchange window";
    public static final String HIDE_WINDOW_LABEL = "Hide exchange window";

    private final Stage stage;
    private final ActorSystem actorSystem;
    private final BitsquareUI application;
    private final TrayIcon trayIcon = createTrayIcon();
    private final MenuItem toggleShowHideItem = new MenuItem(HIDE_WINDOW_LABEL);

    public SystemTray(Stage stage, ActorSystem actorSystem, BitsquareUI application) {
        this.stage = stage;
        this.actorSystem = actorSystem;
        this.application = application;
        init();
    }

    private void init() {
        if (!java.awt.SystemTray.isSupported()) {
            log.error("System tray is not supported.");
            return;
        }

        // prevent exiting the app when the last window gets closed
        Platform.setImplicitExit(false);

        MenuItem aboutItem = new MenuItem("Info about Bitsquare");
        MenuItem exitItem = new MenuItem("Exit");

        PopupMenu popupMenu = new PopupMenu();
        popupMenu.add(aboutItem);
        popupMenu.addSeparator();
        popupMenu.add(toggleShowHideItem);
        popupMenu.addSeparator();
        popupMenu.add(exitItem);

        trayIcon.setPopupMenu(popupMenu);
        trayIcon.setToolTip("Bitsquare: The decentralized bitcoin exchange");

        java.awt.SystemTray self = java.awt.SystemTray.getSystemTray();
        try {
            self.add(trayIcon);
        } catch (AWTException ex) {
            log.error("Icon could not be added to system tray.", ex);
        }

        toggleShowHideItem.addActionListener(e -> {
            if (stage.isShowing()) {
                toggleShowHideItem.setLabel(SHOW_WINDOW_LABEL);
                Platform.runLater(stage::hide);
            }
            else {
                toggleShowHideItem.setLabel(HIDE_WINDOW_LABEL);
                Platform.runLater(stage::show);
            }
        });

        exitItem.addActionListener(e -> {
            self.remove(trayIcon);
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
            } catch (Exception ex) {
                log.error("Application failed to shut down properly.", ex);
            }
        });
    }

    public void hideStage() {
        stage.hide();
        toggleShowHideItem.setLabel(SHOW_WINDOW_LABEL);
    }

    private TrayIcon createTrayIcon() {
        String path = ImageUtil.isRetina() ? ICON_HI_RES : ICON_LO_RES;
        return new TrayIcon(new ImageIcon(getClass().getResource(path)).getImage());
    }
}
