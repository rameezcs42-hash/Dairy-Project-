package com.dairyflow.app.data.model;

/** Milk bought in bulk (from union / supplier / dairy farm) — drives stock. */
public class Purchase {
    public long id;
    public String date;       // yyyy-MM-dd
    public String supplier;
    public String milkType = "buffalo";
    public double litres;
    public double rate;
    public double amount;
    public Double fat;        // FAT % (optional, quality check)
    public String note;
    public long createdAt;
}
