package com.dairyflow.app.data.model;

/** Effective-dated milk rate (sale or purchase). */
public class MilkRate {
    public long id;
    public String kind;          // sale | purchase
    public String milkType;      // cow | buffalo | mixed
    public double rate;
    public String effectiveFrom; // yyyy-MM-dd
}
