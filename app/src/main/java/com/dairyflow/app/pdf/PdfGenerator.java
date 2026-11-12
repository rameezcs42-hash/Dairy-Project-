package com.dairyflow.app.pdf;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;

import com.dairyflow.app.billing.BillingEngine;
import com.dairyflow.app.util.Format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Renders DairyFlow PDFs with the emerald brand:
 *  - monthly customer invoices (multi-page tables, totals, amount in words)
 *  - generic tabular reports (daily / monthly / outstanding / P&L export)
 * Pure framework APIs — works fully offline.
 */
public final class PdfGenerator {

    // A4 @ 72 dpi
    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 34;

    private static final int EMERALD_950 = 0xFF052E22;
    private static final int EMERALD_900 = 0xFF064E3B;
    private static final int EMERALD_700 = 0xFF047857;
    private static final int EMERALD_600 = 0xFF059669;
    private static final int EMERALD_100 = 0xFFD1FAE5;
    private static final int EMERALD_50 = 0xFFECFDF5;
    private static final int GOLD = 0xFFF59E0B;
    private static final int INK = 0xFF10231B;
    private static final int GREY = 0xFF6B7F74;
    private static final int RED = 0xFFDC2626;

    private PdfGenerator() {}

    public static final class InvoiceParams {
        public String shopName, shopPhone, shopAddress, shopTagline, currency;
        public String billingMonth;      // yyyy-MM
        public String billNo;
        public String customerName, customerArea, customerPhone, customerMilkType;
        public double openingBalance;    // balance B/F
        public List<BillingEngine.DeliveryLine> deliveries;
        public List<BillingEngine.PaymentLine> payments;
        public String footerNote;
    }

    public static File invoiceDirectory(android.content.Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Invoices");
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File(ctx.getFilesDir(), "Invoices");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** Generate the invoice and return the PDF file. */
    public static File generateInvoice(android.content.Context ctx, InvoiceParams p) throws IOException {
        PdfDocument doc = new PdfDocument();
        BillingEngine.Statement st = BillingEngine.buildStatement(-1, p.billingMonth,
                p.openingBalance, p.deliveries, p.payments);

        Paint bg = new Paint();
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        int pageNo = 0;
        int y = 0;

        // We build pages manually because tables can span multiple pages.
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNo).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas canvas = page.getCanvas();

        drawHeader(canvas, p, st, EMERALD_900, pageNo, text, fill);
        y = 128;
        y = drawCustomerBlock(canvas, p, y, text, fill);
        y = drawTableHeader(canvas, y, text, fill);

        float rowH = 15.5f;
        boolean zebra = false;
        for (BillingEngine.DeliveryLine l : st.lines) {
            if (y + rowH > PAGE_H - 118) {           // bottom reserve for totals
                drawFooter(canvas, p, pageNo, text, EMERALD_900);
                doc.finishPage(page);
                pageNo++;
                info = new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create();
                page = doc.startPage(info);
                canvas = page.getCanvas();
                drawHeader(canvas, p, st, EMERALD_900, pageNo, text, fill);
                y = 128;
                y = drawCustomerBlock(canvas, p, y, text, fill);
                y = drawTableHeader(canvas, y, text, fill);
            }
            zebra = !zebra;
            if (zebra) {
                fill.setColor(EMERALD_50);
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, fill);
            }
            text.setColor(INK);
            text.setTextSize(8.5f);
            text.setFakeBoldText(false);
            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(Format.pretty(l.date), MARGIN + 6, y + 11, text);
            canvas.drawText(Format.weekdayShort(l.date), MARGIN + 108, y + 11, text);
            String session = l.skipped ? "—" : ("morning".equals(l.session) ? "Morning" : "Evening");
            canvas.drawText(session, MARGIN + 155, y + 11, text);
            text.setTextAlign(Paint.Align.RIGHT);
            if (l.skipped) {
                text.setColor(GREY);
                text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText("OFF", PAGE_W - MARGIN - 105, y + 11, text);
            } else {
                canvas.drawText(Format.litres(l.litres), PAGE_W - MARGIN - 122, y + 11, text);
            }
            canvas.drawText(Format.money(l.rate), PAGE_W - MARGIN - 52, y + 11, text);
            canvas.drawText(Format.money(l.skipped ? 0 : l.amount), PAGE_W - MARGIN - 4, y + 11, text);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(android.graphics.Typeface.DEFAULT);
            y += rowH;
        }
        // table border bottom line
        drawHLine(canvas, MARGIN, PAGE_W - MARGIN, y, EMERALD_700, 1.4f);

        // ---------- totals block ----------
        if (y + 190 > PAGE_H - 40) {
            drawFooter(canvas, p, pageNo, text, EMERALD_900);
            doc.finishPage(page);
            pageNo++;
            info = new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create();
            page = doc.startPage(info);
            canvas = page.getCanvas();
            drawHeader(canvas, p, st, EMERALD_900, pageNo, text, fill);
            y = 150;
        } else {
            y += 12;
        }
        y = drawTotals(canvas, p, st, y, text, fill);
        drawFooter(canvas, p, pageNo, text, EMERALD_900);
        doc.finishPage(page);

        String safe = (p.customerName == null ? "customer" : p.customerName).replaceAll("[^A-Za-z0-9_-]", "_");
        String monthSafe = p.billingMonth.replace("-", "");
        File out = new File(invoiceDirectory(ctx), "Invoice_" + monthSafe + "_" + safe + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            doc.writeTo(fos);
        } finally {
            doc.close();
        }
        return out;
    }

    // ---------------- pieces ----------------

    private static void drawHeader(Canvas canvas, InvoiceParams p, BillingEngine.Statement st,
                                   int bandColor, int pageNo, Paint text, Paint fill) {
        fill.setColor(bandColor);
        canvas.drawRect(0, 0, PAGE_W, 92, fill);

        // gold accent line
        fill.setColor(GOLD);
        canvas.drawRect(0, 92, PAGE_W, 95, fill);

        text.setColor(Color.WHITE);
        text.setTextSize(19f);
        text.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
        text.setTextAlign(Paint.Align.LEFT);
        text.setFakeBoldText(true);
        String shop = p.shopName != null && !p.shopName.trim().isEmpty() ? p.shopName.trim() : "Dairy Shop";
        canvas.drawText(shop, MARGIN, 34, text);

        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setFakeBoldText(false);
        text.setTextSize(8.5f);
        text.setColor(0xFFD1FAE5);
        StringBuilder addr = new StringBuilder();
        if (p.shopAddress != null && !p.shopAddress.isEmpty()) addr.append(p.shopAddress);
        if (p.shopPhone != null && !p.shopPhone.isEmpty()) {
            if (addr.length() > 0) addr.append("   |   ");
            addr.append("Ph: ").append(p.shopPhone);
        }
        if (addr.length() > 0) canvas.drawText(addr.toString(), MARGIN, 51, text);
        if (p.shopTagline != null && !p.shopTagline.isEmpty()) {
            canvas.drawText("\"" + p.shopTagline + "\"", MARGIN, 66, text);
        }

        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(Color.WHITE);
        text.setTextSize(15f);
        text.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
        canvas.drawText("MILK INVOICE", PAGE_W - MARGIN, 30, text);
        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setFakeBoldText(false);
        text.setTextSize(9f);
        text.setColor(0xFFFEF3C7);
        canvas.drawText("Month: " + Format.monthLong(p.billingMonth), PAGE_W - MARGIN, 48, text);
        text.setColor(Color.WHITE);
        canvas.drawText("Invoice # " + (p.billNo != null ? p.billNo : "DF-" + p.billingMonth.replace("-", "")), PAGE_W - MARGIN, 63, text);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(INK);
    }

    private static int drawCustomerBlock(Canvas canvas, InvoiceParams p, int y, Paint text, Paint fill) {
        fill.setColor(EMERALD_50);
        canvas.drawRoundRect(MARGIN, y, PAGE_W - MARGIN, y + 46, 8, 8, fill);

        text.setTextSize(7.5f);
        text.setColor(EMERALD_700);
        text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("BILL TO", MARGIN + 10, y + 13, text);

        text.setColor(INK);
        text.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
        text.setTextSize(12.5f);
        canvas.drawText(p.customerName != null ? p.customerName : "", MARGIN + 10, y + 29, text);

        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setFakeBoldText(false);
        text.setTextSize(8f);
        text.setColor(GREY);
        StringBuilder meta = new StringBuilder();
        if (p.customerArea != null && !p.customerArea.isEmpty()) meta.append(p.customerArea);
        if (p.customerPhone != null && !p.customerPhone.isEmpty()) meta.append(meta.length() > 0 ? "   |   " : "").append(p.customerPhone);
        if (p.customerMilkType != null) meta.append(meta.length() > 0 ? "   |   " : "").append(cap(p.customerMilkType)).append(" milk");
        canvas.drawText(meta.toString(), MARGIN + 10, y + 41, text);

        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(7.5f);
        text.setColor(EMERALD_700);
        text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        canvas.drawText("INVOICE DATE", PAGE_W - MARGIN - 10, y + 13, text);
        text.setColor(INK);
        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setTextSize(9.5f);
        canvas.drawText(new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date()), PAGE_W - MARGIN - 10, y + 29, text);
        text.setTextAlign(Paint.Align.LEFT);
        return y + 58;
    }

    private static int drawTableHeader(Canvas canvas, int y, Paint text, Paint fill) {
        fill.setColor(EMERALD_900);
        canvas.drawRoundRect(MARGIN, y, PAGE_W - MARGIN, y + 17, 4, 4, fill);
        text.setColor(Color.WHITE);
        text.setTextSize(8f);
        text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        text.setTextAlign(Paint.Align.LEFT);
        float ty = y + 12;
        canvas.drawText("DATE", MARGIN + 6, ty, text);
        canvas.drawText("DAY", MARGIN + 108, ty, text);
        canvas.drawText("SESSION", MARGIN + 155, ty, text);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("LITRES", PAGE_W - MARGIN - 122, ty, text);
        canvas.drawText("RATE", PAGE_W - MARGIN - 52, ty, text);
        canvas.drawText("AMOUNT", PAGE_W - MARGIN - 4, ty, text);
        text.setTextAlign(Paint.Align.LEFT);
        return y + 17;
    }

    private static int drawTotals(Canvas canvas, InvoiceParams p, BillingEngine.Statement st,
                                  int y, Paint text, Paint fill) {
        String ccy = p.currency != null && !p.currency.isEmpty() ? p.currency : "Rs";

        // Payment lines summary (compact, up to 6)
        if (!st.payments.isEmpty()) {
            text.setTextSize(8f);
            text.setColor(EMERALD_700);
            text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            canvas.drawText("PAYMENTS RECEIVED THIS MONTH", MARGIN, y + 2, text);
            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setColor(INK);
            float py = y + 12;
            int shown = 0;
            for (BillingEngine.PaymentLine pay : st.payments) {
                if (shown >= 6) break;
                canvas.drawText("•  " + Format.pretty(pay.date) + "  —  " + cap(pay.method) + "  —  " + ccy + " " + Format.money(pay.amount),
                        MARGIN + 6, py + shown * 12, text);
                shown++;
            }
            if (st.payments.size() > 6) {
                canvas.drawText("•  … and " + (st.payments.size() - 6) + " more", MARGIN + 6, py + shown * 12, text);
                shown++;
            }
            y = (int) (py + shown * 12 + 6);
        }

        // Totals box
        int boxW = 232;
        int x0 = PAGE_W - MARGIN - boxW;
        int yy = y;
        fill.setColor(0xFFF4FAF7);
        canvas.drawRoundRect(x0, yy, PAGE_W - MARGIN, yy + 112, 8, 8, fill);

        String[][] rows = {
                {"Balance B/F", fmt(ccy, st.opening)},
                {"Milk charges (" + Format.litres(st.litresTotal) + " L)", fmt(ccy, st.deliveryTotal)},
                {"Gross payable", fmt(ccy, st.opening + st.deliveryTotal)},
                {"Less payments", "- " + fmt(ccy, st.paymentsTotal)},
        };
        text.setTextSize(8.5f);
        int ry = yy + 16;
        for (String[] r : rows) {
            text.setColor(GREY);
            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(r[0], x0 + 10, ry, text);
            text.setColor(INK);
            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(r[1], PAGE_W - MARGIN - 10, ry, text);
            ry += 15;
        }
        drawHLine(canvas, x0 + 10, PAGE_W - MARGIN - 10, ry - 6, EMERALD_700, 1.2f);
        text.setTextSize(11.5f);
        text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        text.setColor(EMERALD_900);
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(st.advance ? "ADVANCE BALANCE" : "CLOSING BALANCE", x0 + 10, ry + 10, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(st.advance ? EMERALD_600 : RED);
        canvas.drawText(fmt(ccy, Math.abs(st.closing)), PAGE_W - MARGIN - 10, ry + 10, text);
        text.setTextAlign(Paint.Align.LEFT);
        y = yy + 112 + 14;

        // Amount in words
        text.setTextSize(8.5f);
        text.setColor(GREY);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        long whole = (long) Math.abs(st.closing);
        canvas.drawText("Amount in words:  " + Format.numberToWords(whole) + " Rupees " + (st.advance ? "(in advance)" : "only"),
                MARGIN, y, text);
        y += 18;

        // Skipped note
        if (st.skippedDays > 0) {
            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setColor(GREY);
            canvas.drawText(st.skippedDays + " day(s) marked as weekly off / holiday are listed as OFF and carry no charge.",
                    MARGIN, y, text);
            y += 16;
        }

        // Terms
        text.setTextSize(7f);
        text.setColor(GREY);
        String terms = p.footerNote != null && !p.footerNote.isEmpty() ? p.footerNote
                : "Payment is requested within 7 days of invoice. Milk once delivered cannot be returned. Rates are exclusive of any dairy board cess.";
        y = drawWrapped(canvas, terms, MARGIN, y + 4, PAGE_W - 2 * MARGIN, 11, text) + 12;

        // Signature
        int sigY = y + 30;
        drawHLine(canvas, PAGE_W - MARGIN - 150, PAGE_W - MARGIN, sigY, INK, 1.1f);
        text.setTextSize(8f);
        text.setColor(INK);
        text.setTextAlign(Paint.Align.CENTER);
        String shop = p.shopName != null ? p.shopName : "Shop";
        canvas.drawText("For " + shop, PAGE_W - MARGIN - 75, sigY + 12, text);
        text.setColor(GREY);
        canvas.drawText("Authorised Signatory", PAGE_W - MARGIN - 75, sigY + 24, text);
        text.setTextAlign(Paint.Align.LEFT);
        return sigY + 30;
    }

    private static void drawFooter(Canvas canvas, InvoiceParams p, int pageNo, Paint text, int bandColor) {
        fillZero().setColor(bandColor);
        canvas.drawRect(0, PAGE_H - 24, PAGE_W, PAGE_H, fillZero());
        text.setColor(Color.WHITE);
        text.setTextSize(7.5f);
        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setTextAlign(Paint.Align.CENTER);
        String shop = p.shopName != null ? p.shopName : "DairyFlow";
        canvas.drawText("Thank you for being a valued customer!  —  " + shop + "   •   Page " + pageNo,
                PAGE_W / 2f, PAGE_H - 9, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private static Paint fillZero() { return new Paint(Paint.ANTI_ALIAS_FLAG); }

    private static void drawHLine(Canvas canvas, float x0, float x1, float y, int color, float width) {
        Paint p = new Paint();
        p.setColor(color);
        p.setStrokeWidth(width);
        canvas.drawLine(x0, y, x1, y, p);
    }

    private static int drawWrapped(Canvas canvas, String text, float x, float y, float maxWidth, float lineHeight, Paint paint) {
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String trial = line.length() == 0 ? w : line + " " + w;
            if (paint.measureText(trial) > maxWidth) {
                canvas.drawText(line.toString(), x, y, paint);
                y += lineHeight;
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, y, paint);
            y += lineHeight;
        }
        return (int) y;
    }

    private static String fmt(String ccy, double v) {
        String sign = v < 0 ? "-" : "";
        return sign + ccy + " " + Format.money(Math.abs(v));
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }
}
