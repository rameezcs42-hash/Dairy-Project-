package com.dairyflow.app.data.model;

/** One milk delivery record. UNIQUE(customer, date, session) protects duplicates. */
public class Delivery {
    public long id;
    public long customerId;
    public String date;        // yyyy-MM-dd
    public String session;     // morning | evening
    public double litres;
    public double rate;        // snapshot Rs/litre at entry time
    public double amount;      // litres * rate (0 when skipped)
    public boolean skipped;    // customer off / shop closed / holiday
    public String note;
    public long createdAt;
    // Transient join fields
    public String customerName;
}
