package com.dairyflow.app.data.model;

public class Payment {
    public long id;
    public long customerId;
    public String date;       // yyyy-MM-dd
    public double amount;
    public String method;     // cash | easypaisa | jazzcash | bank | other
    public String note;
    public long createdAt;
    // Transient
    public String customerName;
}
