package se.systementor;

import se.systementor.model.Product;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Paths;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.utils.Validate;

public class CashRegisterForm {

    private JPanel panel1;
    private JPanel panelRight;
    private JPanel panelLeft;
    private JTextArea receiptArea;
    private JPanel buttonsPanel;
    private JTextField textField1;
    private JTextField textField2;
    private JButton addButton;
    private JButton payButton;
    private JButton clearButton;
    private JButton s3statsButton;
    private Product lastClickedProduct = null;
    private float totalAmount = 0;
    private Database database = new Database();
    private List<Product> selectedProducts = new ArrayList<>();
    private Map<Product, Integer> selectedProductsWithQuantities = new HashMap<>();


    public CashRegisterForm() {

        for (Product product : database.activeProducts()) {
            JButton button = new JButton(product.getProductName());
            buttonsPanel.add(button);

            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    textField1.setText(product.getProductName());
                    lastClickedProduct = product;
                }
            });
        }

        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    int quantity = Integer.parseInt(textField2.getText().trim());
                    if (quantity <= 0) {
                        JOptionPane.showMessageDialog(null, "Please enter a positive number.");
                        return;
                    }

                    if (totalAmount == 0) {
                        Connection connection = null;
                        int receiptNumber = 1; // Default receipt number
                        try {
                            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/suhanasshop",
                                    "root", "Stheyasi*02");

                            String maxOrderIdSql = "SELECT MAX(order_id) FROM orders";
                            PreparedStatement maxOrderStmt = connection.prepareStatement(maxOrderIdSql);
                            ResultSet maxOrderIdResult = maxOrderStmt.executeQuery();

                            if (maxOrderIdResult.next()) {
                                receiptNumber = maxOrderIdResult.getInt(1) + 1;
                            }

                            receiptArea.append("\n========== SUHANA'S STORE ==========\n");
                            receiptArea.append("Receipt Number: " + receiptNumber + "\n");

                            LocalDateTime currentDateTime = LocalDateTime.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            String formattedDateTime = currentDateTime.format(formatter);
                            receiptArea.append("Date/Time: " + formattedDateTime + "\n");
                            receiptArea.append("=====================================\n");

                        } catch (SQLException ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(null, "Error while fetching the receipt number.");
                        } finally {
                            try {
                                if (connection != null) connection.close();
                            } catch (SQLException closeEx) {
                                closeEx.printStackTrace();
                            }
                        }
                    }

                    double lineTotal = quantity * lastClickedProduct.getUnitPrice();
                    receiptArea.append(String.format("%-15s %-10.2f x %-3d = %-10.2f %-3s\n",
                            lastClickedProduct.getProductName(),
                            lastClickedProduct.getUnitPrice(),
                            quantity,
                            lineTotal,
                            lastClickedProduct.getProductCategory()));

                    //receiptArea.append(String.format("%-15s %-10.2f x %-3d = %-10.2f kr\n",
                            //lastClickedProduct.getProductName(), lastClickedProduct.getUnitPrice(), quantity, lineTotal));
                    totalAmount += lineTotal;

                    textField1.setText("");
                    textField2.setText("");

                    selectedProductsWithQuantities.put(lastClickedProduct,
                            selectedProductsWithQuantities.getOrDefault(lastClickedProduct, 0) + quantity);

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(null, "Please enter a valid numeric quantity.");
                }
            }
        });

        payButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                Map<String, Double[]> vatDetails = calculateVAT();
                renderReceipt(vatDetails);

                Connection connection = null;
                try {
                    connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/suhanasshop", "root", "Stheyasi*02");
                    connection.setAutoCommit(false);

                    String orderSql = "INSERT INTO orders (order_date, total_price) VALUES (?, ?)";
                    PreparedStatement orderStmt = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
                    orderStmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                    orderStmt.setBigDecimal(2, BigDecimal.valueOf(totalAmount));
                    orderStmt.executeUpdate();

                    ResultSet generatedKeys = orderStmt.getGeneratedKeys();
                    int orderId = 0;
                    if (generatedKeys.next()) {
                        orderId = generatedKeys.getInt(1);
                    }

                    String orderDetailsSql = "INSERT INTO orderdetails (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)";
                    PreparedStatement orderDetailsStmt = connection.prepareStatement(orderDetailsSql);

                    for (Map.Entry<Product, Integer> entry1 : selectedProductsWithQuantities.entrySet()) {
                        Product product1 = entry1.getKey();
                        int quantity1 = entry1.getValue();

                        orderDetailsStmt.setInt(1, orderId); // Order ID
                        orderDetailsStmt.setInt(2, product1.getProductId()); // Product ID
                        orderDetailsStmt.setInt(3, quantity1); // Quantity
                        orderDetailsStmt.setBigDecimal(4, BigDecimal.valueOf(product1.getUnitPrice())); // Unit price
                        orderDetailsStmt.addBatch();
                    }

                    orderDetailsStmt.executeBatch();
                    connection.commit();
                    JOptionPane.showMessageDialog(null, "Order successfully stored in the database!");

                } catch (SQLException ex) {
                    ex.printStackTrace();
                    try {
                        if (connection != null) {
                            connection.rollback();
                        }
                    } catch (SQLException rollbackEx) {
                        rollbackEx.printStackTrace();
                    }
                } finally {
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        });

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                receiptArea.setText("");
                totalAmount = 0;
                selectedProducts.clear();
            }
        });

        s3statsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {

                    String sql = "SELECT MIN(order_date) AS first_order_time, MAX(order_date) AS last_order_time, " +
                            "SUM(total_price) AS total_sales_incl_vat, SUM(total_price) * 0.1 AS total_vat, " +
                            "COUNT(order_id) AS total_number_of_receipts FROM orders WHERE DATE(order_date) = CURDATE()";

                    Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/suhanasshop", "root", "Stheyasi*02");
                    PreparedStatement stmt = connection.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery();

                    String firstOrderTime = null;
                    String lastOrderTime = null;
                    double totalSalesInclVat = 0;
                    double totalVat = 0;
                    int totalReceipts = 0;

                    if (rs.next()) {
                        firstOrderTime = rs.getString("first_order_time");
                        lastOrderTime = rs.getString("last_order_time");
                        totalSalesInclVat = rs.getDouble("total_sales_incl_vat");
                        totalVat = rs.getDouble("total_vat");
                        totalReceipts = rs.getInt("total_number_of_receipts");
                    }

                    String xmlContent = generateXML(firstOrderTime, lastOrderTime, totalSalesInclVat, totalVat, totalReceipts);
                    String fileName = "S3Stats_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xml";

                    File xmlFile = new File(fileName);
                    try (FileWriter writer = new FileWriter(xmlFile)) {
                        writer.write(xmlContent);
                    }

                    uploadToS3(xmlFile);
                    JOptionPane.showMessageDialog(null, "Daily stats file successfully uploaded to S3!");

                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "An error occurred while generating daily stats.");
                }
            }
        });
    }

    private Map<String, Double[]> calculateVAT() {
        double vatBPercentage = 6.0;
        double vatCPercentage = 12.0;
        double vatDPercentage = 25.0;

        double vatBAmount = 0;
        double vatCAmount = 0;
        double vatDAmount = 0;

        double totalBruttoB = 0;
        double totalBruttoC = 0;
        double totalBruttoD = 0;

        for (Map.Entry<Product, Integer> entry : selectedProductsWithQuantities.entrySet()) {
            Product product = entry.getKey();
            int quantity = entry.getValue();
            double lineTotal = product.getUnitPrice() * quantity;

            if (product.getVatRate() == 12.0) { // VAT category C
                vatCAmount += lineTotal * (vatCPercentage / (100 + vatCPercentage));
                totalBruttoC += lineTotal;
            } else if (product.getVatRate() == 25.0) { // VAT category D
                vatDAmount += lineTotal * (vatDPercentage / (100 + vatDPercentage));
                totalBruttoD += lineTotal;
            } else if (product.getVatRate() == 6.0) { // VAT category B
                vatBAmount += lineTotal * (vatBPercentage / (100 + vatBPercentage));
                totalBruttoB += lineTotal;
            }
        }

        Map<String, Double[]> vatDetails = new HashMap<>();
        vatDetails.put("C", new Double[]{vatCPercentage, vatCAmount, totalBruttoC - vatCAmount, totalBruttoC});
        vatDetails.put("D", new Double[]{vatDPercentage, vatDAmount, totalBruttoD - vatDAmount, totalBruttoD});
        vatDetails.put("B", new Double[]{vatBPercentage, vatBAmount, totalBruttoB - vatBAmount, totalBruttoB});

        return vatDetails;
    }

    private void renderReceipt(Map<String, Double[]> vatDetails) {
        receiptArea.append("=====================================\n");
        receiptArea.append(String.format("Total: %.2f kr\n", totalAmount));
        receiptArea.append("=====================================\n");
        receiptArea.append(String.format("%-10s %-10s %-10s %-10s\n",
               "Moms%", "Moms", "Netto", "Brutto"));



        for (Map.Entry<String, Double[]> entry : vatDetails.entrySet()) {
            Double[] values = entry.getValue(); // {Moms%, MomsAmount, Netto, Brutto}
            receiptArea.append(String.format(" %-10.2f %-10.2f %-10.2f %-10.2f\n",
                    values[0], values[1], values[2], values[3]));
        }

        //receiptArea.append("=====================================\n");
        //receiptArea.append(String.format("Total: %.2f kr\n", totalAmount));
        receiptArea.append("=====================================\n");
        receiptArea.append("Thanks for stopping by! See you again soon :)\n");
    }


    private String generateXML(String firstOrderTime, String lastOrderTime, double totalSalesInclVat,
                               double totalVat, int totalReceipts) {
        return "<SaleStatistics>\n" +
                "    <FirstOrderDateTime>" + firstOrderTime + "</FirstOrderDateTime>\n" +
                "    <LastOrderDateTime>" + lastOrderTime + "</LastOrderDateTime>\n" +
                "    <TotalSalesInclVat>" + totalSalesInclVat + "</TotalSalesInclVat>\n" +
                "    <TotalVat>" + totalVat + "</TotalVat>\n" +
                "    <TotalNumberOfReceipts>" + totalReceipts + "</TotalNumberOfReceipts>\n" +
                "</SaleStatistics>";
    }

    private void uploadToS3(File file) throws Exception {
        String bucketName = "suhana-jensen";
        String accessKey = "AKIA4SZHOFK3RAWXXXXX"; // Replace with actual access key
        String secretKey = "Qk70GuWtgBdTR0Jj9CC7HWkJ38WZOsldlZRYYYYY"; // Replace with actual secret key
        String region = "eu-north-1";

        S3Client s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .build();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(file.getName()) // Use file name as S3 object key
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromFile(Paths.get(file.getAbsolutePath())));
        System.out.println("File uploaded successfully to bucket: " + bucketName);
    }

    public void run() {
        JFrame frame = new JFrame("Cash Register Form");
        frame.setContentPane(new CashRegisterForm().panel1);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setSize(1000, 400);
        frame.setVisible(true);
    }

    private void createUIComponents() {
        buttonsPanel = new JPanel();
        receiptArea = new JTextArea();
        textField1 = new JTextField();
        textField2 = new JTextField();
        addButton = new JButton("Add");
        payButton = new JButton("Pay");
        clearButton = new JButton("Clear");
        s3statsButton = new JButton("S3stats");
    }
}
