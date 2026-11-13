package com.dairyflow.app.data.model;

/** Customer = a regular milk client (kiranas, households, sweet shops, hotels...). */
public class Customer {
    public long id;
    public String name;
    public String phone;
    public String shopName;
    public String address;
    public String area;
    public String milkType = "buffalo";      // cow | buffalo | mixed
    public double defaultRate;               // Rs per litre
    public double openingBalance;            // Rs owed at start (+) / advance (-)
    public double morningQty;                // default litres, morning
    public double eveningQty;                // default litres, evening
    public int weeklyOff;                    // 0=none, 1=Mon .. 7=Sun
    public String sessionPref = "both";      // morning | evening | both
    public String note;
    public boolean active = true;
    public long createdAt;
    // Transient, computed for lists
    public double balance;
    public double monthLitres;
    public double monthTotal;

    public boolean receivesMorning() { return !"evening".equals(sessionPref); }
    public boolean receivesEvening() { return !"morning".equals(sessionPref); }
}
