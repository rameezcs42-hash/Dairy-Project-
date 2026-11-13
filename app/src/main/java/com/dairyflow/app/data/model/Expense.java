package com.dairyflow.app.data.model;

public class Expense {
    public long id;
    public String date;
    public String category;   // rent | salary | transport | electricity | ice | misc
    public double amount;
    public String note;
    public long createdAt;
}
