package com.example.finalproject;


import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.print.attribute.standard.Destination;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TravelItineraryManagerApp extends Application {

    // --- Constants ---
    private static final String DATA_FILE = "travel_data_singlefile.txt";
    private static final String DELIMITER = "::";
    private static final String TRIP_MARKER = "##TRIP##";
    private static final String DEST_MARKER = "##DEST##";
    private static final String EVENT_MARKER = "##EVENT##";
    private static final String END_MARKER = "##END##";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // --- Core Data ---
    private ObservableList<Trip> tripData = FXCollections.observableArrayList();
    private ObservableList<Destination> currentDestinationList = FXCollections.observableArrayList();
    private ObservableList<ItineraryEvent> currentItineraryList = FXCollections.observableArrayList();

    // --- UI Elements ---
    private ListView<Trip> tripListView;
    private ListView<Destination> destinationListView;
    private ListView<ItineraryEvent> itineraryListView;
    private TextField destinationSearchField;
    private ComboBox<String> destinationSortComboBox;
    private TextField eventSearchField;
    private ComboBox<String> eventSortComboBox;
    private TextArea destinationDetailsArea;
    private TextArea userNotesArea;

    private Trip selectedTrip;
    private Destination selectedDestination;

    // --- Main Application Entry Point ---
    public static void main(String[] args) {
        launch(args);
    }
    private void searchInBrowser(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = new URI("https://www.google.com/search?q=" + encoded);
            java.awt.Desktop.getDesktop().browse(uri);
        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Web Search Error", "Unable to open browser.");
        }
    }

    // --- JavaFX Application Start ---
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Travel Itinerary Manager (Single File)");

        // Load data before UI setup
        loadData();

        // --- Create UI Programmatically ---
        BorderPane rootLayout = new BorderPane();
        rootLayout.setPadding(new Insets(10));

        // --- Set Background Image ---
        try {
            Image bgImage = new Image("file:C:/Users/vladk/Downloads/22176-1920x1080-desktop-1080p-island-background (2).jpg", 1000, 700, false, true);

            BackgroundSize backgroundSize = new BackgroundSize(100, 100, true, true, true, false);
            BackgroundImage backgroundImage = new BackgroundImage(
                    bgImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    backgroundSize
            );
            rootLayout.setBackground(new Background(backgroundImage));
        } catch (Exception e) {
            System.out.println("Background image not loaded. Reason: " + e.getMessage());
        }

        // Left Pane: Trips
        VBox tripsPane = createTripsPane();
        rootLayout.setLeft(tripsPane);
        BorderPane.setMargin(tripsPane, new Insets(0, 10, 0, 0));

        // Center Pane: Destinations and Itinerary
        SplitPane centerSplitPane = createCenterSplitPane();
        rootLayout.setCenter(centerSplitPane);

        // --- Setup Scene ---
        Scene scene = new Scene(rootLayout, 1000, 700);
        primaryStage.setScene(scene);
        primaryStage.show();

        // --- Initial State ---
        tripListView.setItems(tripData);
        setupSelectionListeners();
        populateSortCombos();
        clearDestinationDetails();
        clearItineraryList();
    }

    // --- UI Creation Helpers ---

    private VBox createTripsPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(5));
        Label tripsLabel = new Label("My Trips");
        tripsLabel.setStyle("-fx-font-weight: bold;");
        tripListView = new ListView<>();
        tripListView.setPrefHeight(600);
        tripListView.setCellFactory(lv -> new ListCell<Trip>() {
            @Override
            protected void updateItem(Trip item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.toString());
            }
        });

        HBox buttons = new HBox(5);
        Button addTripBtn = new Button("Add");
        Button editTripBtn = new Button("Edit");
        Button delTripBtn = new Button("Delete");
        addTripBtn.setOnAction(e -> handleAddTrip());
        editTripBtn.setOnAction(e -> handleEditTrip());
        delTripBtn.setOnAction(e -> handleDeleteTrip());
        buttons.getChildren().addAll(addTripBtn, editTripBtn, delTripBtn);

        vbox.getChildren().addAll(tripsLabel, tripListView, buttons);
        return vbox;
    }

    private SplitPane createCenterSplitPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.setDividerPositions(0.5);

        VBox destinationsPane = createDestinationsPane();
        VBox itineraryPane = createItineraryPane();

        splitPane.getItems().addAll(destinationsPane, itineraryPane);
        return splitPane;
    }

    private VBox createDestinationsPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(5));

        Label destLabel = new Label("Destinations for Selected Trip");
        destLabel.setStyle("-fx-font-weight: bold;");

        HBox searchSortBox = new HBox(10);
        destinationSearchField = new TextField();
        destinationSearchField.setPromptText("Search Destinations...");
        Button destSearchBtn = new Button("Search");
        destSearchBtn.setOnAction(e -> handleSearchDestination());
        destinationSortComboBox = new ComboBox<>();
        destinationSortComboBox.setPromptText("Sort By...");
        Button destSortBtn = new Button("Sort");
        destSortBtn.setOnAction(e -> handleSortDestination());
        searchSortBox.getChildren().addAll(destinationSearchField, destSearchBtn, destinationSortComboBox, destSortBtn);
        searchSortBox.setAlignment(Pos.CENTER_LEFT);

        destinationListView = new ListView<>();
        destinationListView.setPrefHeight(150);
        destinationListView.setCellFactory(lv -> new ListCell<Destination>() {
            @Override
            protected void updateItem(Destination item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.toString());
            }
        });

        HBox destButtons = new HBox(5);
        Button addDestBtn = new Button("Add");
        Button editDestBtn = new Button("Edit");
        Button delDestBtn = new Button("Delete");
        addDestBtn.setOnAction(e -> handleAddDestination());
        editDestBtn.setOnAction(e -> handleEditDestination());
        delDestBtn.setOnAction(e -> handleDeleteDestination());
        destButtons.getChildren().addAll(addDestBtn, editDestBtn, delDestBtn);

        Label detailsLabel = new Label("Destination Details:");
        destinationDetailsArea = new TextArea();
        destinationDetailsArea.setPrefRowCount(5);
        destinationDetailsArea.setEditable(false);
        destinationDetailsArea.setWrapText(true);

        Label notesLabel = new Label("User Notes / Tips:");
        userNotesArea = new TextArea();
        userNotesArea.setPrefRowCount(3);
        userNotesArea.setWrapText(true);
        userNotesArea.setPromptText("Add personal notes/tips for the selected destination here...");
        userNotesArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedDestination != null && newVal != null) {
                selectedDestination.setTravelTips(newVal);
            }
        });

        vbox.getChildren().addAll(destLabel, searchSortBox, destinationListView, destButtons, detailsLabel, destinationDetailsArea, notesLabel, userNotesArea);
        return vbox;
    }

    private VBox createItineraryPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(5));

        Label itineraryLabel = new Label("Itinerary for Selected Destination");
        itineraryLabel.setStyle("-fx-font-weight: bold;");

        HBox searchSortBox = new HBox(10);
        eventSearchField = new TextField();
        eventSearchField.setPromptText("Search Events...");
        Button eventSearchBtn = new Button("Search");
        eventSearchBtn.setOnAction(e -> handleSearchEvent());
        eventSortComboBox = new ComboBox<>();
        eventSortComboBox.setPromptText("Sort By...");
        Button eventSortBtn = new Button("Sort");
        eventSortBtn.setOnAction(e -> handleSortEvent());
        searchSortBox.getChildren().addAll(eventSearchField, eventSearchBtn, eventSortComboBox, eventSortBtn);
        searchSortBox.setAlignment(Pos.CENTER_LEFT);

        itineraryListView = new ListView<>();
        itineraryListView.setPrefHeight(150);
        itineraryListView.setCellFactory(lv -> new ListCell<ItineraryEvent>() {
            @Override
            protected void updateItem(ItineraryEvent item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.toString());
            }
        });

        HBox eventButtons = new HBox(5);
        Button addEventBtn = new Button("Add");
        Button editEventBtn = new Button("Edit");
        Button delEventBtn = new Button("Delete");
        addEventBtn.setOnAction(e -> handleAddEvent());
        editEventBtn.setOnAction(e -> handleEditEvent());
        delEventBtn.setOnAction(e -> handleDeleteEvent());
        eventButtons.getChildren().addAll(addEventBtn, editEventBtn, delEventBtn);

        Button exportButton = new Button("Export Trip Summary");
        exportButton.setOnAction(e -> handleExport());

        vbox.getChildren().addAll(itineraryLabel, searchSortBox, itineraryListView, eventButtons, exportButton);
        return vbox;
    }

    // --- Populate UI Elements ---

    private void populateSortCombos() {
        destinationSortComboBox.getItems().addAll("Name Asc", "Name Desc");
        eventSortComboBox.getItems().addAll("Date/Time Asc", "Date/Time Desc", "Cost Asc", "Cost Desc", "Type");
    }

    // --- Event Handlers & Logic ---

    private void setupSelectionListeners() {
        tripListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showTripDetails(newValue));

        destinationListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showDestinationDetails(newValue));
    }

    private void showTripDetails(Trip trip) {
        selectedTrip = trip;
        currentDestinationList.clear();
        if (trip != null) {
            currentDestinationList.addAll(trip.getDestinations());
        }
        destinationListView.setItems(currentDestinationList);
        showDestinationDetails(null);
        destinationSearchField.clear();
    }

    private void showDestinationDetails(Destination destination) {
        selectedDestination = destination;
        clearItineraryList();
        clearDestinationDetails();

        if (destination != null) {
            destination.getItinerary().forEach(currentItineraryList::add);
            itineraryListView.setItems(currentItineraryList);

            StringBuilder details = new StringBuilder();
            details.append("Location: ").append(destination.getLocationDetails()).append("\n");
            details.append("Accommodation: ").append(destination.getAccommodation()).append("\n");
            details.append("Points of Interest: ").append(destination.getPointsOfInterest()).append("\n");
            details.append("Weather Insights: ").append(destination.getLocalWeatherInsights()).append("\n");
            destinationDetailsArea.setText(details.toString());

            userNotesArea.setText(destination.getTravelTips());
            userNotesArea.setDisable(false);
        } else {
            userNotesArea.setDisable(true);
        }
        eventSearchField.clear();
    }

    private void clearDestinationDetails() {
        destinationDetailsArea.clear();
        userNotesArea.clear();
        userNotesArea.setDisable(true);
    }

    private void clearItineraryList() {
        currentItineraryList.clear();
        itineraryListView.setItems(currentItineraryList);
    }

    // --- CRUD Handlers ---

    private void handleAddTrip() {
        Trip tempTrip = new Trip("", LocalDate.now(), LocalDate.now().plusDays(1), 0.0);
        Optional<Trip> result = showTripEditDialog(tempTrip, "New Trip");
        result.ifPresent(tripData::add);
    }

    private void handleEditTrip() {
        Trip selected = tripListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Optional<Trip> result = showTripEditDialog(selected, "Edit Trip");
            result.ifPresent(editedTrip -> {
                int index = tripData.indexOf(selected);
                if (index != -1) {
                    tripData.set(index, editedTrip);
                    tripListView.getSelectionModel().select(index);
                } else {
                    showAlert(AlertType.ERROR, "Update Error", "Could not find the trip to update.");
                }
                showTripDetails(editedTrip);
            });
        } else {
            showAlert(AlertType.WARNING, "No Selection", "Please select a trip in the list.");
        }
    }

    private void handleDeleteTrip() {
        Trip selected = tripListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle("Confirm Deletion");
            alert.setHeaderText("Delete Trip: " + selected.getTripName());
            alert.setContentText("Are you sure you want to delete this trip and all its data?");
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                tripData.remove(selected);
            }
        } else {
            showAlert(AlertType.WARNING, "No Selection", "Please select a trip in the list.");
        }
    }

    private void handleAddDestination() {
        if (selectedTrip == null) {
            showAlert(AlertType.WARNING, "No Trip Selected", "Please select a trip first.");
            return;
        }
        Destination tempDest = new Destination("", "", "", "", "", "");
        Optional<Destination> result = showDestinationEditDialog(tempDest, "New Destination");
        result.ifPresent(newDest -> {
            selectedTrip.addDestination(newDest);
            currentDestinationList.add(newDest);
            destinationListView.getSelectionModel().select(newDest);
        });
    }

    private void handleEditDestination() {
        Destination selected = destinationListView.getSelectionModel().getSelectedItem();
        if (selected != null && selectedTrip != null) {
            Optional<Destination> result = showDestinationEditDialog(selected, "Edit Destination");
            result.ifPresent(editedDest -> {
                int index = currentDestinationList.indexOf(selected);
                if (index != -1) {
                    currentDestinationList.set(index, editedDest);
                    destinationListView.getSelectionModel().select(editedDest);
                } else {
                    destinationListView.refresh();
                }
                showDestinationDetails(editedDest);
            });
        } else {
            showAlert(AlertType.WARNING, "No Selection", "Please select a destination in the list.");
        }
    }

    private void handleDeleteDestination() {
        Destination selected = destinationListView.getSelectionModel().getSelectedItem();
        if (selected != null && selectedTrip != null) {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle("Confirm Deletion");
            alert.setHeaderText("Delete Destination: " + selected.getName());
            alert.setContentText("Are you sure?");
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                selectedTrip.removeDestination(selected);
                currentDestinationList.remove(selected);
            }
        } else {
            showAlert(AlertType.WARNING, "No Selection", "Please select a destination.");
        }
    }

    private void handleAddEvent() {
        if (selectedDestination == null) {
            showAlert(AlertType.WARNING, "No Destination Selected", "Please select a destination first.");
            return;
        }
        ItineraryEvent tempEvent = new ItineraryEvent("Activity", "", LocalDateTime.now(), 0.0, "");
        Optional<ItineraryEvent> result = showItineraryEditDialog(tempEvent, "New Event");
        result.ifPresent(newEvent -> {
            selectedDestination.addItineraryEvent(newEvent);
            currentItineraryList.add(newEvent);
            itineraryListView.getSelectionModel().select(newEvent);
        });
    }

    private void handleEditEvent() {
        ItineraryEvent selected = itineraryListView.getSelectionModel().getSelectedItem();
        if (selected != null && selectedDestination != null) {
            Optional<ItineraryEvent> result = showItineraryEditDialog(selected, "Edit Event");
            result.ifPresent(editedEvent -> {
                refreshItineraryList();
                itineraryListView.getSelectionModel().select(editedEvent);
            });
        } else {
            showAlert(AlertType.WARNING, "No Selection", "Please select an itinerary event.");
        }
    }

    private void handleDeleteEvent() {
        ItineraryEvent selected = itineraryListView.getSelectionModel().getSelectedItem();
        if (selected != null && selectedDestination != null) {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle("Confirm Deletion");
            alert.setHeaderText("Delete Event: " + selected.getDescription());
            alert.setContentText("Are you sure?");
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                boolean removed = selectedDestination.removeItineraryEvent(selected);
                if (removed) {
                    currentItineraryList.remove(selected);
                } else {
                    showAlert(AlertType.ERROR, "Deletion Failed", "Could not remove the event.");
                    refreshItineraryList();
                }
            }
        } else {
            showAlert(AlertType.WARNING, "No Selection", "Please select an event.");
        }
    }

    // --- Search and Sort Handlers ---

    private void handleSearchDestination() {
        String searchTerm = destinationSearchField.getText().trim().toLowerCase();
        if (selectedTrip == null) return;

        if (searchTerm.isEmpty()) {
            currentDestinationList.setAll(selectedTrip.getDestinations());
        } else {
            List<Destination> filtered = selectedTrip.getDestinations().stream()
                    .filter(d -> d.getName().toLowerCase().contains(searchTerm) ||
                            d.getLocationDetails().toLowerCase().contains(searchTerm))
                    .collect(Collectors.toList());
            currentDestinationList.setAll(filtered);
        }
        destinationListView.setItems(currentDestinationList);
    }

    private void handleSortDestination() {
        if (selectedTrip == null || currentDestinationList.isEmpty()) return;
        String sortBy = destinationSortComboBox.getValue();
        if (sortBy == null) {
            showAlert(AlertType.WARNING, "Sort Error", "Please choose how to sort destinations.");
            return;
        }

        Comparator<Destination> comparator = null;
        switch (sortBy) {
            case "Name Asc":
                comparator = Comparator.comparing(Destination::getName, String.CASE_INSENSITIVE_ORDER);
                break;
            case "Name Desc":
                comparator = Comparator.comparing(Destination::getName, String.CASE_INSENSITIVE_ORDER).reversed();
                break;
            default:
                return;
        }

        mergeSort(currentDestinationList, comparator);
        System.out.println("Sorted destinations by: " + sortBy);
    }

    private void handleSearchEvent() {
        String searchTerm = eventSearchField.getText().trim().toLowerCase();
        if (selectedDestination == null) return;

        List<ItineraryEvent> allEvents = new ArrayList<>();
        selectedDestination.getItinerary().forEach(allEvents::add);

        if (searchTerm.isEmpty()) {
            currentItineraryList.setAll(allEvents);
        } else {
            List<ItineraryEvent> filtered = allEvents.stream()
                    .filter(e -> e.getDescription().toLowerCase().contains(searchTerm) ||
                            e.getType().toLowerCase().contains(searchTerm))
                    .collect(Collectors.toList());
            currentItineraryList.setAll(filtered);
        }
        itineraryListView.setItems(currentItineraryList);
    }

    private void handleSortEvent() {
        if (selectedDestination == null || currentItineraryList.isEmpty()) return;
        String sortBy = eventSortComboBox.getValue();
        if (sortBy == null) {
            showAlert(AlertType.WARNING, "Sort Error", "Please choose how to sort events.");
            return;
        }

        Comparator<ItineraryEvent> comparator = null;
        switch (sortBy) {
            case "Date/Time Asc":
                comparator = Comparator.comparing(ItineraryEvent::getDateTime);
                break;
            case "Date/Time Desc":
                comparator = Comparator.comparing(ItineraryEvent::getDateTime).reversed();
                break;
            case "Cost Asc":
                comparator = Comparator.comparingDouble(ItineraryEvent::getCost);
                break;
            case "Cost Desc":
                comparator = Comparator.comparingDouble(ItineraryEvent::getCost).reversed();
                break;
            case "Type":
                comparator = Comparator.comparing(ItineraryEvent::getType, String.CASE_INSENSITIVE_ORDER);
                break;
            default:
                return;
        }

        mergeSort(currentItineraryList, comparator);
        System.out.println("Sorted events by: " + sortBy);
    }

    private void handleExport() {
        if (selectedTrip == null) {
            showAlert(AlertType.WARNING, "Export Error", "Please select a trip to export.");
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Trip Summary: ").append(selectedTrip.getTripName()).append("\n");
        summary.append("Dates: ").append(selectedTrip.getStartDate().format(DATE_FORMATTER))
                .append(" to ").append(selectedTrip.getEndDate().format(DATE_FORMATTER)).append("\n");
        summary.append("Budget: $").append(String.format("%.2f", selectedTrip.getBudget())).append("\n\n");

        for (Destination dest : selectedTrip.getDestinations()) {
            summary.append("--- Destination: ").append(dest.getName()).append(" ---").append("\n");
            summary.append("  Location: ").append(dest.getLocationDetails()).append("\n");
            summary.append("  Accommodation: ").append(dest.getAccommodation()).append("\n");
            summary.append("  Points of Interest: ").append(dest.getPointsOfInterest()).append("\n");
            summary.append("  Weather Notes: ").append(dest.getLocalWeatherInsights()).append("\n");
            summary.append("  User Tips: ").append(dest.getTravelTips()).append("\n");
            summary.append("  Itinerary:\n");

            if (dest.getItinerary().isEmpty()) {
                summary.append("    - No events planned.\n");
            } else {
                List<ItineraryEvent> sortedEvents = new ArrayList<>();
                dest.getItinerary().forEach(sortedEvents::add);
                mergeSort(sortedEvents, Comparator.comparing(ItineraryEvent::getDateTime));

                for (ItineraryEvent event : sortedEvents) {
                    summary.append(String.format("    - [%s] %s (%s) Cost: $%.2f Notes: %s\n",
                            event.getType(),
                            event.getDescription(),
                            event.getDateTime().format(DATETIME_FORMATTER),
                            event.getCost(),
                            event.getNotes()));
                }
            }
            summary.append("\n");
        }

        TextArea textArea = new TextArea(summary.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        ScrollPane scrollPane = new ScrollPane(textArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        Alert exportDialog = new Alert(AlertType.INFORMATION);
        exportDialog.setTitle("Trip Summary Export");
        exportDialog.setHeaderText("Summary for: " + selectedTrip.getTripName());
        exportDialog.getDialogPane().setContent(scrollPane);
        exportDialog.setResizable(true);
        exportDialog.showAndWait();
    }

    private void refreshItineraryList() {
        currentItineraryList.clear();
        if (selectedDestination != null) {
            selectedDestination.getItinerary().forEach(currentItineraryList::add);
        }
        itineraryListView.setItems(currentItineraryList);
    }

    // --- Helper Methods ---

    private void showAlert(AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // --- Dialog Creation Methods ---

    private Optional<Trip> showTripEditDialog(Trip trip, String title) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(tripListView.getScene().getWindow());
        dialogStage.setTitle(title);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField(trip.getTripName());
        DatePicker startDatePicker = new DatePicker(trip.getStartDate());
        DatePicker endDatePicker = new DatePicker(trip.getEndDate());
        TextField budgetField = new TextField(String.valueOf(trip.getBudget()));

        grid.add(new Label("Trip Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Start Date:"), 0, 1);
        grid.add(startDatePicker, 1, 1);
        grid.add(new Label("End Date:"), 0, 2);
        grid.add(endDatePicker, 1, 2);
        grid.add(new Label("Budget ($):"), 0, 3);
        grid.add(budgetField, 1, 3);

        Button okButton = new Button("OK");
        Button cancelButton = new Button("Cancel");

        final boolean[] okClicked = {false};

        okButton.setOnAction(e -> {
            if (isTripInputValid(nameField, startDatePicker, endDatePicker, budgetField)) {
                trip.setTripName(nameField.getText());
                trip.setStartDate(startDatePicker.getValue());
                trip.setEndDate(endDatePicker.getValue());
                try {
                    trip.setBudget(Double.parseDouble(budgetField.getText()));
                } catch (NumberFormatException ex) {
                    showAlert(AlertType.ERROR, "Invalid Input", "Budget must be a number.");
                    return;
                }
                okClicked[0] = true;
                dialogStage.close();
            }
        });

        cancelButton.setOnAction(e -> dialogStage.close());

        HBox buttonBar = new HBox(10, okButton, cancelButton);
        buttonBar.setAlignment(Pos.BOTTOM_RIGHT);
        grid.add(buttonBar, 1, 4);

        Scene dialogScene = new Scene(grid);
        dialogStage.setScene(dialogScene);
        dialogStage.showAndWait();

        return okClicked[0] ? Optional.of(trip) : Optional.empty();
    }

    private boolean isTripInputValid(TextField nameField, DatePicker startPicker, DatePicker endPicker, TextField budgetField) {
        String errorMessage = "";
        if (nameField.getText() == null || nameField.getText().isEmpty()) {
            errorMessage += "No valid trip name!\n";
        }
        if (startPicker.getValue() == null) {
            errorMessage += "No valid start date!\n";
        }
        if (endPicker.getValue() == null) {
            errorMessage += "No valid end date!\n";
        }
        if (startPicker.getValue() != null && endPicker.getValue() != null && endPicker.getValue().isBefore(startPicker.getValue())) {
            errorMessage += "End date cannot be before start date!\n";
        }
        try {
            if (budgetField.getText() == null || budgetField.getText().isEmpty()) {
                errorMessage += "No valid budget!\n";
            } else {
                Double.parseDouble(budgetField.getText());
            }
        } catch (NumberFormatException e) {
            errorMessage += "Budget must be a valid number!\n";
        }

        if (errorMessage.isEmpty()) {
            return true;
        } else {
            showAlert(AlertType.ERROR, "Invalid Fields", errorMessage);
            return false;
        }
    }

    private Optional<Destination> showDestinationEditDialog(Destination dest, String title) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(destinationListView.getScene().getWindow());
        dialogStage.setTitle(title);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField(dest.getName());
        Button webSearchBtn = new Button("Search Web");
        webSearchBtn.setOnAction(ev -> {
            String place = nameField.getText().trim();
            if (!place.isEmpty()) {
                searchInBrowser(place);
            } else {
                showAlert(AlertType.WARNING, "Input Required", "Please enter a destination name first.");
            }
        });

        HBox nameRow = new HBox(10, nameField, webSearchBtn);
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameRow, 1, 0);

        TextArea locationField = new TextArea(dest.getLocationDetails());
        locationField.setPrefRowCount(2);
        TextArea poiField = new TextArea(dest.getPointsOfInterest());
        poiField.setPrefRowCount(3);
        TextArea accomField = new TextArea(dest.getAccommodation());
        accomField.setPrefRowCount(2);
        TextArea weatherField = new TextArea(dest.getLocalWeatherInsights());
        weatherField.setPrefRowCount(2);
        TextArea tipsField = new TextArea(dest.getTravelTips());
        tipsField.setPrefRowCount(3);

        grid.add(new Label("Location Details:"), 0, 1);
        grid.add(locationField, 1, 1);
        grid.add(new Label("Accommodation:"), 0, 2);
        grid.add(accomField, 1, 2);
        grid.add(new Label("Points of Interest:"), 0, 3);
        grid.add(poiField, 1, 3);
        grid.add(new Label("Weather Insights:"), 0, 4);
        grid.add(weatherField, 1, 4);
        grid.add(new Label("Travel Tips:"), 0, 5);
        grid.add(tipsField, 1, 5);

        Button okButton = new Button("OK");
        Button cancelButton = new Button("Cancel");
        final boolean[] okClicked = {false};

        okButton.setOnAction(e -> {
            if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
                showAlert(AlertType.ERROR, "Invalid Input", "Destination name cannot be empty.");
                return;
            }
            dest.setName(nameField.getText());
            dest.setLocationDetails(locationField.getText());
            dest.setPointsOfInterest(poiField.getText());
            dest.setAccommodation(accomField.getText());
            dest.setLocalWeatherInsights(weatherField.getText());
            dest.setTravelTips(tipsField.getText());
            okClicked[0] = true;
            dialogStage.close();
        });

        cancelButton.setOnAction(e -> dialogStage.close());

        HBox buttonBar = new HBox(10, okButton, cancelButton);
        buttonBar.setAlignment(Pos.BOTTOM_RIGHT);
        grid.add(buttonBar, 1, 6);

        Scene dialogScene = new Scene(grid);
        dialogStage.setScene(dialogScene);
        dialogStage.showAndWait();

        return okClicked[0] ? Optional.of(dest) : Optional.empty();
    }



    private Optional<ItineraryEvent> showItineraryEditDialog(ItineraryEvent event, String title) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(itineraryListView.getScene().getWindow());
        dialogStage.setTitle(title);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList("Flight", "Hotel", "Train", "Bus", "Car Rental", "Tour", "Activity", "Meal", "Meeting", "Other"));
        typeCombo.setValue(event.getType());
        TextField descField = new TextField(event.getDescription());
        DatePicker datePicker = new DatePicker(event.getDateTime().toLocalDate());
        TextField timeField = new TextField(event.getDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        TextField costField = new TextField(String.valueOf(event.getCost()));
        TextArea notesArea = new TextArea(event.getNotes());
        notesArea.setPrefRowCount(3);

        grid.add(new Label("Type:"), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("Date:"), 0, 2);
        grid.add(datePicker, 1, 2);
        grid.add(new Label("Time (HH:mm):"), 0, 3);
        grid.add(timeField, 1, 3);
        grid.add(new Label("Cost ($):"), 0, 4);
        grid.add(costField, 1, 4);
        grid.add(new Label("Notes:"), 0, 5);
        grid.add(notesArea, 1, 5);

        Button okButton = new Button("OK");
        Button cancelButton = new Button("Cancel");
        final boolean[] okClicked = {false};

        okButton.setOnAction(e -> {
            String error = "";
            if (typeCombo.getValue() == null || typeCombo.getValue().isEmpty()) error += "Type is required.\n";
            if (descField.getText() == null || descField.getText().trim().isEmpty()) error += "Description is required.\n";
            if (datePicker.getValue() == null) error += "Date is required.\n";
            LocalTime time;
            try {
                time = LocalTime.parse(timeField.getText(), DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException ex) {
                error += "Invalid time format (use HH:mm).\n";
                time = null;
            }
            double cost;
            try {
                cost = Double.parseDouble(costField.getText());
                if (cost < 0) error += "Cost cannot be negative.\n";
            } catch (NumberFormatException ex) {
                error += "Invalid cost format.\n";
                cost = 0;
            }

            if (!error.isEmpty()) {
                showAlert(AlertType.ERROR, "Invalid Input", error);
                return;
            }

            event.setType(typeCombo.getValue());
            event.setDescription(descField.getText());
            event.setDateTime(LocalDateTime.of(datePicker.getValue(), time));
            event.setCost(cost);
            event.setNotes(notesArea.getText());
            okClicked[0] = true;
            dialogStage.close();
        });
        cancelButton.setOnAction(e -> dialogStage.close());

        HBox buttonBar = new HBox(10, okButton, cancelButton);
        buttonBar.setAlignment(Pos.BOTTOM_RIGHT);
        grid.add(buttonBar, 1, 6);

        Scene dialogScene = new Scene(grid);
        dialogStage.setScene(dialogScene);
        dialogStage.showAndWait();

        return okClicked[0] ? Optional.of(event) : Optional.empty();
    }

    // --- File I/O (Data Persistence) ---

    @Override
    public void stop() {
        saveData();
    }

    private void loadData() {
        try {
            List<Trip> loadedTrips = loadTripsFromFile(DATA_FILE);
            tripData.setAll(loadedTrips);
            System.out.println("Loaded " + tripData.size() + " trips from " + DATA_FILE);
        } catch (IOException e) {
            System.err.println("Could not load data from " + DATA_FILE + ": " + e.getMessage());
            showAlert(AlertType.ERROR, "Load Error", "Could not load travel data.\n" + e.getMessage());
        }
    }

    private void saveData() {
        try {
            saveTripsToFile(tripData, DATA_FILE);
            System.out.println("Saved " + tripData.size() + " trips to " + DATA_FILE);
        } catch (IOException e) {
            System.err.println("Could not save data to " + DATA_FILE + ": " + e.getMessage());
            showAlert(AlertType.ERROR, "Save Error", "Could not save travel data.\n" + e.getMessage());
        }
    }

    // --- Static Nested Model Classes ---

    public static class Trip {
        private String tripName;
        private LocalDate startDate;
        private LocalDate endDate;
        private double budget;
        private List<Destination> destinations;

        public Trip(String tripName, LocalDate startDate, LocalDate endDate, double budget) {
            this.tripName = tripName;
            this.startDate = startDate;
            this.endDate = endDate;
            this.budget = budget;
            this.destinations = new ArrayList<>();
        }

        public String getTripName() { return tripName; }
        public void setTripName(String tripName) { this.tripName = tripName; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public double getBudget() { return budget; }
        public void setBudget(double budget) { this.budget = budget; }
        public List<Destination> getDestinations() { return destinations; }

        public void addDestination(Destination destination) { this.destinations.add(destination); }
        public boolean removeDestination(Destination destination) { return this.destinations.remove(destination); }

        @Override
        public String toString() {
            return String.format("%s (%s - %s)", tripName,
                    startDate != null ? startDate.format(DATE_FORMATTER) : "N/A",
                    endDate != null ? endDate.format(DATE_FORMATTER) : "N/A");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Trip trip = (Trip) o;
            return Objects.equals(tripName, trip.tripName) && Objects.equals(startDate, trip.startDate) && Objects.equals(endDate, trip.endDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tripName, startDate, endDate);
        }
    }

    public static class Destination implements Comparable<Destination> {
        private String name;
        private String locationDetails;
        private String pointsOfInterest;
        private String accommodation;
        private String localWeatherInsights;
        private String travelTips;
        private CustomLinkedList<ItineraryEvent> itinerary;

        public Destination(String name, String locationDetails, String pointsOfInterest, String accommodation, String localWeatherInsights, String travelTips) {
            this.name = name;
            this.locationDetails = locationDetails;
            this.pointsOfInterest = pointsOfInterest;
            this.accommodation = accommodation;
            this.localWeatherInsights = localWeatherInsights;
            this.travelTips = travelTips;
            this.itinerary = new CustomLinkedList<>();
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLocationDetails() { return locationDetails; }
        public void setLocationDetails(String locationDetails) { this.locationDetails = locationDetails; }
        public String getPointsOfInterest() { return pointsOfInterest; }
        public void setPointsOfInterest(String pointsOfInterest) { this.pointsOfInterest = pointsOfInterest; }
        public String getAccommodation() { return accommodation; }
        public void setAccommodation(String accommodation) { this.accommodation = accommodation; }
        public String getLocalWeatherInsights() { return localWeatherInsights; }
        public void setLocalWeatherInsights(String localWeatherInsights) { this.localWeatherInsights = localWeatherInsights; }
        public String getTravelTips() { return travelTips; }
        public void setTravelTips(String travelTips) { this.travelTips = travelTips; }
        public CustomLinkedList<ItineraryEvent> getItinerary() { return itinerary; }

        public void addItineraryEvent(ItineraryEvent event) { this.itinerary.add(event); }
        public boolean removeItineraryEvent(ItineraryEvent event) { return this.itinerary.remove(event); }

        @Override
        public String toString() { return name; }

        @Override
        public int compareTo(Destination other) {
            return this.name.compareToIgnoreCase(other.name);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Destination that = (Destination) o;
            return Objects.equals(name, that.name) && Objects.equals(locationDetails, that.locationDetails);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, locationDetails);
        }
    }

    public static class ItineraryEvent implements Comparable<ItineraryEvent> {
        private String type;
        private String description;
        private LocalDateTime dateTime;
        private double cost;
        private String notes;

        public ItineraryEvent(String type, String description, LocalDateTime dateTime, double cost, String notes) {
            this.type = type;
            this.description = description;
            this.dateTime = dateTime;
            this.cost = cost;
            this.notes = notes;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public LocalDateTime getDateTime() { return dateTime; }
        public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
        public double getCost() { return cost; }
        public void setCost(double cost) { this.cost = cost; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        @Override
        public String toString() {
            return String.format("[%s] %s (%s) $%.2f", type, description, dateTime != null ? dateTime.format(DATETIME_FORMATTER) : "N/A", cost);
        }

        @Override
        public int compareTo(ItineraryEvent other) {
            if (this.dateTime == null && other.dateTime == null) return 0;
            if (this.dateTime == null) return 1;
            if (other.dateTime == null) return -1;
            return this.dateTime.compareTo(other.dateTime);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItineraryEvent event = (ItineraryEvent) o;
            return Double.compare(event.cost, cost) == 0 &&
                    Objects.equals(type, event.type) &&
                    Objects.equals(description, event.description) &&
                    Objects.equals(dateTime, event.dateTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, description, dateTime, cost);
        }
    }

    public static class CustomLinkedList<T> implements Iterable<T> {
        private Node<T> head;
        private int size;

        private static class Node<T> {
            T data;
            Node<T> next;

            Node(T data) {
                this.data = data;
                this.next = null;
            }
        }

        public CustomLinkedList() {
            head = null;
            size = 0;
        }

        public void add(T data) {
            Node<T> newNode = new Node<>(data);
            if (head == null) {
                head = newNode;
            } else {
                Node<T> current = head;
                while (current.next != null) {
                    current = current.next;
                }
                current.next = newNode;
            }
            size++;
        }

        public boolean remove(T data) {
            if (head == null) return false;
            if (Objects.equals(head.data, data)) {
                head = head.next;
                size--;
                return true;
            }
            Node<T> current = head;
            while (current.next != null && !Objects.equals(current.next.data, data)) {
                current = current.next;
            }
            if (current.next != null) {
                current.next = current.next.next;
                size--;
                return true;
            }
            return false;
        }

        public T get(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            Node<T> current = head;
            for (int i = 0; i < index; i++) {
                current = current.next;
            }
            return current.data;
        }

        public int size() { return size; }
        public boolean isEmpty() { return size == 0; }

        @Override
        public Iterator<T> iterator() {
            return new LinkedListIterator();
        }

        public void forEach(java.util.function.Consumer<? super T> action) {
            Objects.requireNonNull(action);
            for (Node<T> current = head; current != null; current = current.next) {
                action.accept(current.data);
            }
        }

        private class LinkedListIterator implements Iterator<T> {
            private Node<T> current = head;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                T data = current.data;
                current = current.next;
                return data;
            }
        }
    }

    // --- Static Utility Methods (Data I/O, Search, Sort) ---

    private static void saveTripsToFile(List<Trip> trips, String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (Trip trip : trips) {
                writer.write(TRIP_MARKER);
                writer.newLine();
                writer.write(escape(trip.getTripName()) + DELIMITER +
                        (trip.getStartDate() != null ? trip.getStartDate().format(DATE_FORMATTER) : "") + DELIMITER +
                        (trip.getEndDate() != null ? trip.getEndDate().format(DATE_FORMATTER) : "") + DELIMITER +
                        trip.getBudget());
                writer.newLine();

                for (Destination dest : trip.getDestinations()) {
                    writer.write(DEST_MARKER);
                    writer.newLine();
                    writer.write(escape(dest.getName()) + DELIMITER +
                            escape(dest.getLocationDetails()) + DELIMITER +
                            escape(dest.getPointsOfInterest()) + DELIMITER +
                            escape(dest.getAccommodation()) + DELIMITER +
                            escape(dest.getLocalWeatherInsights()) + DELIMITER +
                            escape(dest.getTravelTips()));
                    writer.newLine();

                    dest.getItinerary().forEach(event -> {
                        try {
                            writer.write(EVENT_MARKER);
                            writer.newLine();
                            writer.write(escape(event.getType()) + DELIMITER +
                                    escape(event.getDescription()) + DELIMITER +
                                    (event.getDateTime() != null ? event.getDateTime().format(DATETIME_FORMATTER) : "") + DELIMITER +
                                    event.getCost() + DELIMITER +
                                    escape(event.getNotes()));
                            writer.newLine();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    writer.write(END_MARKER);
                    writer.newLine();
                }
                writer.write(END_MARKER);
                writer.newLine();
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static List<Trip> loadTripsFromFile(String filename) throws IOException {
        List<Trip> trips = new ArrayList<>();
        File file = new File(filename);
        if (!file.exists()) return trips;

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            Trip currentTrip = null;
            Destination currentDestination = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equals(TRIP_MARKER)) {
                    currentTrip = null;
                    currentDestination = null;
                    String dataLine = reader.readLine();
                    if (dataLine == null) break;
                    String[] parts = dataLine.split(DELIMITER, 4);
                    if (parts.length == 4) {
                        try {
                            LocalDate start = parts[1].isEmpty() ? null : LocalDate.parse(parts[1], DATE_FORMATTER);
                            LocalDate end = parts[2].isEmpty() ? null : LocalDate.parse(parts[2], DATE_FORMATTER);
                            currentTrip = new Trip(unescape(parts[0]), start, end, Double.parseDouble(parts[3]));
                            trips.add(currentTrip);
                        } catch (DateTimeParseException | NumberFormatException e) {
                            System.err.println("Skipping malformed trip: " + dataLine + " (" + e.getMessage() + ")");
                        }
                    }
                } else if (line.equals(DEST_MARKER) && currentTrip != null) {
                    currentDestination = null;
                    String dataLine = reader.readLine();
                    if (dataLine == null) break;
                    String[] parts = dataLine.split(DELIMITER, 6);
                    if (parts.length == 6) {
                        currentDestination = new Destination(
                                unescape(parts[0]), unescape(parts[1]), unescape(parts[2]),
                                unescape(parts[3]), unescape(parts[4]), unescape(parts[5])
                        );
                        currentTrip.addDestination(currentDestination);
                    } else {
                        System.err.println("Skipping malformed destination: " + dataLine);
                    }
                } else if (line.equals(EVENT_MARKER) && currentDestination != null) {
                    String dataLine = reader.readLine();
                    if (dataLine == null) break;
                    String[] parts = dataLine.split(DELIMITER, 5);
                    if (parts.length == 5) {
                        try {
                            LocalDateTime dateTime = parts[2].isEmpty() ? null : LocalDateTime.parse(parts[2], DATETIME_FORMATTER);
                            ItineraryEvent event = new ItineraryEvent(
                                    unescape(parts[0]), unescape(parts[1]), dateTime,
                                    Double.parseDouble(parts[3]), unescape(parts[4])
                            );
                            currentDestination.addItineraryEvent(event);
                        } catch (DateTimeParseException | NumberFormatException e) {
                            System.err.println("Skipping malformed event: " + dataLine + " (" + e.getMessage() + ")");
                        }
                    }
                }
            }
        }
        return trips;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace(DELIMITER, "\\" + DELIMITER);
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\" + DELIMITER, DELIMITER).replace("\\r", "\r").replace("\\n", "\n").replace("\\\\", "\\");
    }

    public static <T> void mergeSort(List<T> list, Comparator<? super T> comparator) {
        if (list.size() <= 1) return;
        int mid = list.size() / 2;
        List<T> left = new ArrayList<>(list.subList(0, mid));
        List<T> right = new ArrayList<>(list.subList(mid, list.size()));
        mergeSort(left, comparator);
        mergeSort(right, comparator);
        merge(list, left, right, comparator);
    }

    private static <T> void merge(List<T> result, List<T> left, List<T> right, Comparator<? super T> comparator) {
        int i = 0, j = 0, k = 0;
        while (i < left.size() && j < right.size()) {
            if (comparator.compare(left.get(i), right.get(j)) <= 0) {
                result.set(k++, left.get(i++));
            } else {
                result.set(k++, right.get(j++));
            }
        }
        while (i < left.size()) result.set(k++, left.get(i++));
        while (j < right.size()) result.set(k++, right.get(j++));
    }

    // --- Binary Search Methods (Optional) ---

    public static int binarySearchDestinationByName(List<Destination> sortedDestinations, String nameKey) {
        int low = 0;
        int high = sortedDestinations.size() - 1;
        String lowerCaseKey = nameKey.toLowerCase();

        while (low <= high) {
            int mid = low + (high - low) / 2;
            Destination midVal = sortedDestinations.get(mid);
            int cmp = midVal.getName().toLowerCase().compareTo(lowerCaseKey);

            if (cmp < 0) low = mid + 1;
            else if (cmp > 0) high = mid - 1;
            else return mid;
        }
        return -1;
    }

    public static int binarySearchEventByDescription(List<ItineraryEvent> sortedEvents, String descriptionKey) {
        int low = 0;
        int high = sortedEvents.size() - 1;
        String lowerCaseKey = descriptionKey.toLowerCase();

        while (low <= high) {
            int mid = low + (high - low) / 2;
            ItineraryEvent midVal = sortedEvents.get(mid);
            int cmp = midVal.getDescription().toLowerCase().compareTo(lowerCaseKey);

            if (cmp < 0) low = mid + 1;
            else if (cmp > 0) high = mid - 1;
            else return mid;
        }
        return -1;
    }
}
