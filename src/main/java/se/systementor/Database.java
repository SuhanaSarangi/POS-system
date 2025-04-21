package se.systementor;

import se.systementor.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    String url = "jdbc:mysql://localhost:3306/suhanasshop";
    String user = "root";
    String password = "Stheyasi*02";


    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public List<Product> activeProducts() {
        ArrayList<Product> products = new ArrayList<Product>();

        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM product WHERE product_discontinued = 0");

            while (rs.next()) {

                Product product = new Product();
                product.setProductId(rs.getInt("product_id"));
                product.setProductName(rs.getString("product_name"));
                product.setUnitPrice(rs.getFloat("unit_price"));
                product.setProductDiscontinued(rs.getBoolean("product_discontinued"));
                product.setVatRate(rs.getFloat("VATRate"));
                product.setProductCategory(rs.getString("category_id"));
                products.add(product);
            }
            rs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return products;
    }

}
