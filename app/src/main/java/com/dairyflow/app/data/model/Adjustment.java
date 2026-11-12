package com.dairyflow.app.data.model;

/** Signed stock correction: spoilage (-), return (+), recount (+/-). */
public class Adjustment {
    public long id;
    public String date;
    public double litres;     // negative = wasted
    public String reason;     // spoil | wastage | return | count
    public String note;
    public long createdAt;
}
