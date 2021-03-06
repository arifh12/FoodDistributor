package main;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <h1>PurchaseOrderController</h1>
 * <p>
 *     This class is used for handling of the inputs from and outputs to PurchaseOrder.fxml. It uses a builder design
 *     pattern in order to create a Purchase Order from PurchaseOrder, Item, and PurchaseOrderDAO objects. This class
 *     is primarily responsible for facilitating the data between the front-end and the back-end of the application.
 * </p>
 *
 * @author Arif Hasan
 * @version 1.0
 * @since 03/29/21
 */
public class PurchaseOrderController implements Initializable {

    // JavaFX GUI component declarations
    @FXML
    private TextField tfSearch;
    @FXML
    private TextField tfQuantity;
    @FXML
    private Button btnSearch;
    @FXML
    private Button btnBack;
    @FXML
    private Button btnLogout;
    @FXML
    private ComboBox<String> cbItem;
    @FXML
    private DatePicker dateNeedBy;
    @FXML
    private FlowPane paneOrder;
    @FXML
    private Button btnAdd;
    @FXML
    private ListView<Integer> listPurchaseOrders;
    @FXML
    private TableColumn<PurchaseOrder, String> colItem;
    @FXML
    private TableColumn<PurchaseOrder, String> colNeedBy;
    @FXML
    private TableColumn<PurchaseOrder, Double> colQuantity;
    @FXML
    private TableColumn<PurchaseOrder, String> colSubtotal;
    @FXML
    private TableView<PurchaseOrder> tblOrder;
    @FXML
    private TextField tfTotal;
    @FXML
    private Button btnSubmit;
    @FXML
    private Button btnClear;
    @FXML
    private Button btnView;

    private final int MIN_ITEMS = 1;
    private final int MAX_ITEMS = 5;
    private final int OOS_COUNT = 2; //number of out of stock items to trigger the alert
    private final PurchaseOrderDAO purchaseDAO = new PurchaseOrderDAO();
    private String vendorName;
    private List<Item> vendorItems;
    private List<Integer> purchaseIdList;
    private ObservableList<PurchaseOrder> orderList;
    private double total = 0;

    /**
     * All the GUI components of the Purchase Order are initialized when the window is opened.
     *
     * @param url auto-generated parameter; not used
     * @param resourceBundle auto-generated parameter; not used
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        orderList = FXCollections.observableArrayList();

        // Retrieves a list of existing purchase ID's
        try {
            checkStock();
            purchaseIdList = purchaseDAO.getPurchaseIds("");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        // Hide the purchase order controls until a vendor is searched
        paneOrder.setVisible(false);
        dateNeedBy.setDayCellFactory(getMinNeedByCalendar());
        initializeTable();

        // Restricting inputs to numeric/decimal
        tfQuantity.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String oldVal, String newVal) {
                if (newVal.length() > 0 && !newVal.matches("([0-9]*)(\\.([0-9]*))?"))
                    tfQuantity.setText(newVal.substring(0, newVal.length() - 1));
            }
        });

        // Listeners for each of the buttons
        btnSearch.setOnAction(event -> {
            clearAll();
            vendorName = tfSearch.getText();
            try {
                listPurchaseOrders.setItems(FXCollections.observableList(purchaseDAO.getPurchaseIds(vendorName)));
                vendorItems = purchaseDAO.getSearchVendor(vendorName);
                if (vendorItems != null && !vendorItems.isEmpty()) {
                    paneOrder.setVisible(true);
                    List<String> itemNames = vendorItems.stream().map(Item::getName).collect(Collectors.toList());
                    cbItem.setItems(FXCollections.observableList(itemNames));
                } else {
                    paneOrder.setVisible(false);
                    AlertController a = new AlertController(Alert.AlertType.ERROR, "Vendor not found!",
                            "Vendor does not exist or has any items in stock!");
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        });

        btnAdd.setOnAction(event -> {
            if (inputValidation() && orderList.size() < MAX_ITEMS) {
                addToPurchaseOrder();
            } else if (orderList.size() >= MAX_ITEMS) {
                AlertController a = new AlertController(Alert.AlertType.WARNING, "Limit Reached",
                        "You have reached the maximum number of items: " + MAX_ITEMS);
            }
            else {
                AlertController a = new AlertController(Alert.AlertType.ERROR, "Invalid Entry",
                        "Ensure that the inputs are correct!");
            }
        });

        btnSubmit.setOnAction(event -> {
            if (orderList.size() >= MIN_ITEMS && orderList.size() <= MAX_ITEMS) {
                submitPurchaseOrder();
            }
        });

        btnClear.setOnAction(event -> {
            clearAll();
        });

        btnView.setOnAction(event -> {
            int purchaseId;
            if (listPurchaseOrders.getSelectionModel().getSelectedItem() != null) {
                purchaseId = listPurchaseOrders.getSelectionModel().getSelectedItem();
                try {
                    orderList = FXCollections.observableList(purchaseDAO.getPurchaseOrder(purchaseId));
                    initializeTable();
                    total = orderList.stream().mapToDouble(order -> Double.parseDouble(order.getSubtotal())).sum();
                    tfTotal.setText(String.format("$%.2f", total));
                    btnSubmit.setDisable(true);
                    btnAdd.setDisable(true);
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                }
            }
        });

        btnBack.setOnAction(event -> {
            try {
                Stage stage = new Stage();
                FXMLLoader loader = new FXMLLoader();
                Pane root = loader.load(getClass().getResource("Dashboard.fxml").openStream());
                Scene scene = new Scene(root);
                scene.getStylesheets().setAll(
                        getClass().getResource("style.css").toExternalForm()
                );
                stage.setScene(scene);
                stage.show();
                ((Node) event.getSource()).getScene().getWindow().hide();
            } catch (Exception ex) {
                System.out.println(ex);
            }
        });

        btnLogout.setOnAction(event -> {
            try {
                Stage stage = new Stage();
                FXMLLoader loader = new FXMLLoader();
                Pane root = loader.load(getClass().getResource("LoginScreen.fxml").openStream());
                Scene scene = new Scene(root);
                stage.setScene(scene);
                stage.show();
                ((Node) event.getSource()).getScene().getWindow().hide();
            } catch (Exception ex) {
                System.out.println(ex);
            }
        });
    }

    /**
     * Displays an alert when more than 2 items are out of stock
     *
     * @throws SQLException
     */
    private void checkStock() throws SQLException {
        if(purchaseDAO.getStockInfo(OOS_COUNT)) {
            AlertController a = new AlertController(Alert.AlertType.WARNING, "Stock Warning",
                    "More than " + OOS_COUNT + " items are out of stock!");
        }
    }

    /**
     * This method is used for the initialization of the table that displays the full purchase order.
     */
    public void initializeTable() {
        colItem.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colNeedBy.setCellValueFactory(new PropertyValueFactory<>("needBy"));
        colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        tblOrder.setItems(orderList);
    }

    /**
     * This method is to ensure that the user is not allowed to select a previous date as the 'need by' date.
     *
     * @return Callback object for the calendar which restricts past dates
     */
    public Callback<DatePicker, DateCell> getMinNeedByCalendar() {
        return new Callback<>() {
            @Override
            public DateCell call(final DatePicker datePicker) {
                return new DateCell() {
                    @Override
                    public void updateItem(LocalDate item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item.isBefore(LocalDate.now())) {
                            setDisable(true);
                            setStyle("-fx-background-color: #ffc0cb;");
                        }
                    }
                };
            }
        };
    }

    /**
     * Ensures that all fields contain valid information.
     *
     * @return <code>true</code> if all the inputs are valid; otherwise, <code>false</code>.
     */
    public boolean inputValidation() {
        if (cbItem.getValue() == null || cbItem.getValue().isBlank())
            return false;
        if (dateNeedBy.getValue() == null)
            return false;
        if (tfQuantity.getText().isEmpty() ||
                tfQuantity.getText().equals(".") ||
                Double.parseDouble(tfQuantity.getText()) < 0)
            return false;

        return true;
    }

    /**
     * Adds the entered item to the purchase order and updates the total.
     */
    public void addToPurchaseOrder() {
        String itemName = cbItem.getValue();
        String needBy = String.valueOf(dateNeedBy.getValue());
        double quantity = Double.parseDouble(tfQuantity.getText());
        Item item = vendorItems.stream().filter(p -> p.getName() == itemName).findFirst().orElse(null);

        PurchaseOrder order = new PurchaseOrder(item, needBy, quantity);
        orderList.add(order);

        total += Double.parseDouble(order.getSubtotal());
        tfTotal.setText(String.format("$%.2f", total));

        cbItem.setValue("");
        dateNeedBy.setValue(null);
        tfQuantity.setText("");
    }

    /**
     * Resets all the input fields to original state.
     */
    public void clearAll() {
        btnAdd.setDisable(false);
        btnSubmit.setDisable(false);
        total = 0;
        cbItem.setValue("");
        dateNeedBy.setValue(null);
        tfQuantity.setText("");
        tfTotal.setText("");

        if (orderList != null)
            orderList.clear();
    }

    /**
     * Uses PurchaseOrderDAO to add the entered purchase order to the database.
     */
    public void submitPurchaseOrder() {
        try {
            int purchaseId = purchaseIdList.isEmpty() ?
                    1 : purchaseIdList.stream().max(Comparator.naturalOrder()).get() + 1 ;
            if (purchaseDAO.insertPurchaseOrder(orderList, vendorName, purchaseId)) {
                AlertController a = new AlertController(Alert.AlertType.INFORMATION, "Success!",
                        "Purchase order has been successfully submitted!");
                purchaseIdList.add(purchaseId);
                listPurchaseOrders.getItems().add(purchaseId);
                clearAll();
            } else {
                AlertController a = new AlertController(Alert.AlertType.ERROR, "Error!",
                        "Could not submit purchase order. Recheck all entries!");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}

