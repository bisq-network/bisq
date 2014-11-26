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

package io.bitsquare.gui.main.account.content.restrictions;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.gui.main.account.arbitrator.browser.ArbitratorBrowserView;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;

import java.util.Locale;

import javax.inject.Inject;

import viewfx.view.FxmlView;
import viewfx.view.View;
import viewfx.view.ViewLoader;
import viewfx.view.Wizard;
import viewfx.view.support.ActivatableViewAndModel;
import viewfx.view.support.CachingViewLoader;

import javafx.fxml.FXML;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;

@FxmlView
public class RestrictionsView extends ActivatableViewAndModel<GridPane, RestrictionsViewModel> implements Wizard.Step {

    @FXML ListView<Locale> languagesListView;
    @FXML ListView<Country> countriesListView;
    @FXML ListView<Arbitrator> arbitratorsListView;
    @FXML ComboBox<Locale> languageComboBox;
    @FXML ComboBox<Region> regionComboBox;
    @FXML ComboBox<Country> countryComboBox;
    @FXML Button completedButton, addAllEuroCountriesButton;

    private Wizard wizard;

    private final ViewLoader viewLoader;
    private final Stage primaryStage;

    @Inject
    private RestrictionsView(RestrictionsViewModel model, CachingViewLoader viewLoader, Stage primaryStage) {
        super(model);
        this.viewLoader = viewLoader;
        this.primaryStage = primaryStage;
    }

    @Override
    public void initialize() {
        initLanguage();
        initCountry();
        initArbitrators();

        completedButton.disableProperty().bind(model.doneButtonDisable);
    }

    @Override
    public void doActivate() {
        languagesListView.setItems(model.getLanguageList());
        countriesListView.setItems(model.getCountryList());
        arbitratorsListView.setItems(model.getArbitratorList());
    }

    @Override
    public void setWizard(Wizard wizard) {
        this.wizard = wizard;
    }

    @Override
    public void hideWizardNavigation() {
        root.getChildren().remove(completedButton);
    }

    @FXML
    private void onAddLanguage() {
        model.addLanguage(languageComboBox.getSelectionModel().getSelectedItem());
        languageComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    private void onSelectRegion() {
        countryComboBox.setVisible(true);
        Region region = regionComboBox.getSelectionModel().getSelectedItem();
        countryComboBox.setItems(model.getAllCountriesFor(region));

        addAllEuroCountriesButton.setVisible(region.getCode().equals("EU"));
    }

    @FXML
    private void onAddCountry() {
        model.addCountry(countryComboBox.getSelectionModel().getSelectedItem());
        countryComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    private void onAddAllEuroCountries() {
        countriesListView.setItems(model.getListWithAllEuroCountries());
    }

    @FXML
    private void onOpenArbitratorScreen(){
        View view = viewLoader.load(ArbitratorBrowserView.class);
        showStage(view);
    }


    @FXML
    private void onCompleted() {
            wizard.nextStep(this);
    }

    @FXML
    private void onOpenLanguagesHelp() {
        Help.openWindow(HelpId.SETUP_RESTRICTION_LANGUAGES);
    }

    @FXML
    private void onOpenCountriesHelp() {
        Help.openWindow(HelpId.SETUP_RESTRICTION_COUNTRIES);
    }

    @FXML
    private void onOpenArbitratorsHelp() {
        Help.openWindow(HelpId.SETUP_RESTRICTION_ARBITRATORS);
    }

    private void showStage(View view) {
        final Stage stage = new Stage();
        stage.setTitle("Arbitrator selection");
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setWidth(800);
        stage.setHeight(600);
        stage.setX(primaryStage.getX() + 50);
        stage.setY(primaryStage.getY() + 50);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(primaryStage);
        Scene scene = new Scene((Parent) view.getRoot(), 800, 600);
        stage.setScene(scene);
        stage.setOnHidden(windowEvent -> {
            if (view instanceof ArbitratorBrowserView)
                updateArbitratorList();
        });
        stage.show();
    }

    void updateArbitratorList() {
        model.updateArbitratorList();
        arbitratorsListView.setItems(model.getArbitratorList());
    }

    private void initLanguage() {
        languagesListView.setCellFactory(new Callback<ListView<Locale>, ListCell<Locale>>() {
            @Override
            public ListCell<Locale> call(ListView<Locale> list) {
                return new ListCell<Locale>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final Locale item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getDisplayName());
                            removeButton.setOnAction(actionEvent -> removeLanguage(item));
                            setGraphic(pane);
                        }
                        else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });

        languageComboBox.setItems(model.getAllLanguages());
        languageComboBox.setConverter(new StringConverter<Locale>() {
            @Override
            public String toString(Locale locale) {
                return locale.getDisplayLanguage();
            }

            @Override
            public Locale fromString(String s) {
                return null;
            }
        });
    }

    private void initCountry() {
        regionComboBox.setItems(model.getAllRegions());
        regionComboBox.setConverter(new StringConverter<io.bitsquare.locale.Region>() {
            @Override
            public String toString(io.bitsquare.locale.Region region) {
                return region.getName();
            }

            @Override
            public io.bitsquare.locale.Region fromString(String s) {
                return null;
            }
        });

        countriesListView.setCellFactory(new Callback<ListView<Country>, ListCell<Country>>() {
            @Override
            public ListCell<Country> call(ListView<Country> list) {
                return new ListCell<Country>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final Country item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getName());
                            removeButton.setOnAction(actionEvent -> removeCountry(item));
                            setGraphic(pane);
                        }
                        else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });

        countryComboBox.setConverter(new StringConverter<Country>() {
            @Override
            public String toString(Country country) {
                return country.getName();
            }

            @Override
            public Country fromString(String s) {
                return null;
            }
        });
    }

    private void initArbitrators() {
        arbitratorsListView.setCellFactory(new Callback<ListView<Arbitrator>, ListCell<Arbitrator>>() {
            @Override
            public ListCell<Arbitrator> call(ListView<Arbitrator> list) {
                return new ListCell<Arbitrator>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final Arbitrator item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getName());
                            removeButton.setOnAction(actionEvent -> removeArbitrator(item));
                            setGraphic(pane);
                        }
                        else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }

    private void removeLanguage(Locale locale) {
        model.removeLanguage(locale);
    }

    private void removeCountry(Country country) {
        model.removeCountry(country);
    }

    private void removeArbitrator(Arbitrator arbitrator) {
        model.removeArbitrator(arbitrator);
    }
}

