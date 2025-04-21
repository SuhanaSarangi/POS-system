package se.systementor.model;

public class Product {

    private int productId;
    private String productName;
    private float unitPrice;
    private float vatRate;
    private boolean productDiscontinued;
    private String productCategory;

    public Product() {
    }


    public Product(int productId, String productName, float unitPrice, float vatRate) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
    }


        public String getProductName() {
        return productName;
    }
    public void setProductName(String productName) {
        this.productName = productName;
    }

    public float getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(float unitPrice) {
        this.unitPrice = unitPrice;
    }

    public boolean isProductDiscontinued() {
        return productDiscontinued;
    }

    public void setProductDiscontinued(boolean productDiscontinued) {
        this.productDiscontinued = productDiscontinued;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }
    public double getVatRate() {
        return vatRate;
    }
    public void setVatRate(float vatRate) {
        this.vatRate = vatRate;
    }
    public String getProductCategory() {
        return productCategory;
    }
    public void setProductCategory(String productCategory) {
        this.productCategory = productCategory;
    }

}


