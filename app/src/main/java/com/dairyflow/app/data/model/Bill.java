package com.dairyflow.app.data.model;

/** Frozen summary of a customer's month (regenerated on demand). */
public class Bill {
    public long id;
    public long customerId;
    public String month;          // yyyy-MM
    public double opening;
    public double litres;
    public double deliveryTotal;
    public double paymentsTotal;
    public double closing;
    public int skippedDays;
    public int deliveryDays;
    public String pdfPath;
    public String generatedAt;
    // Transient
    public String customerName;
    public String customerArea;
}
