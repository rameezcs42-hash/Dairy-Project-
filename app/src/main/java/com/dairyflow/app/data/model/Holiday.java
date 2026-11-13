package com.dairyflow.app.data.model;

/** One-off dated holiday for a specific customer (Eid, Shab-e-Meraj, etc.). */
public class Holiday {
    public long id;
    public long customerId;
    public String date;           // yyyy-MM-dd
    public String note;
}
