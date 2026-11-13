package com.dairyflow.app.pdf;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;

import com.dairyflow.app.util.Format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Simple branded tabular PDF used by all Reports exports. */
public final class ReportPdf {

    private static final int PAGE_W = 595, PAGE_H = 842, MARGIN = 36;
    private static final int EMERALD_900 = 0xFF064E3B, EMERALD_DARK = 0xFF0B3B2E;
    private static final int EMERALD_50 = 0xFFECFDF5, GREY = 0xFF6B7F74, INK = 0xFF10231B;
    private static final int EMERALD_TEXT = 0xFF064E3B;

    public static final class Table {
        public String title;
        public String subtitle;
        public String shopName, currency;
        public String[] columns;          // e.g. {"Date","Litres","Amount"}
        public float[] columnWeights;     // relative widths
        public boolean[] rightAlign;
        public List<String[]> rows;
        public String[] summary;          // optional footer lines
    }

    private ReportPdf() {}

    public static File reportDirectory(android.content.Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Reports");
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File(ctx.getFilesDir(), "Reports");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** Small page session: owns current page/canvas and draws branded headers/footers. */
    private static final class Session {
        final PdfDocument doc = new PdfDocument();
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float[] colX;
        final float rowH = 16f;
        final int contentBottom = PAGE_H - 44;
        final Table t;
        int pageNo = 0;
        int y;
        PdfDocument.Page page;
        Canvas canvas;

        Session(Table t) {
            this.t = t;
            int usable = PAGE_W - 2 * MARGIN;
            float tw = 0;
            for (float w : t.columnWeights) tw += w;
            colX = new float[t.columnWeights.length + 1];
            float x = MARGIN;
            for (int i = 0; i < t.columnWeights.length; i++) {
                colX[i] = x;
                x += usable * (t.columnWeights[i] / tw);
            }
            colX[t.columnWeights.length] = PAGE_W - MARGIN;
            newPage(true);
        }

        void newPage(boolean firstPage) {
            if (page != null) {
                drawFooter();
                doc.finishPage(page);
            }
            pageNo++;
            page = doc.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create());
            canvas = page.getCanvas();
            if (firstPage) {
                fill.setColor(EMERALD_900);
                canvas.drawRect(0, 0, PAGE_W, 64, fill);
                fill.setColor(0xFFF59E0B);
                canvas.drawRect(0, 64, PAGE_W, 67, fill);
                text.setColor(Color.WHITE);
                text.setTextSize(16f);
                text.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
                text.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(t.shopName != null ? t.shopName : "DairyFlow", MARGIN, 27, text);
                text.setTextSize(10.5f);
                text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText(t.title, MARGIN, 49, text);
                text.setTextSize(8.5f);
                text.setColor(0xFFD1FAE5);
                text.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(t.subtitle != null ? t.subtitle : "", PAGE_W - MARGIN, 49, text);
                text.setTextAlign(Paint.Align.LEFT);
                y = 84;
            } else {
                fill.setColor(EMERALD_900);
                canvas.drawRect(0, 0, PAGE_W, 34, fill);
                text.setColor(Color.WHITE);
                text.setTextSize(10f);
                text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
                canvas.drawText(t.title + "  (cont.)", MARGIN, 21, text);
                y = 44;
            }
            drawColumnHeader();
        }

        void drawColumnHeader() {
            fill.setColor(EMERALD_DARK);
            canvas.drawRoundRect(MARGIN, y, PAGE_W - MARGIN, y + 18, 4, 4, fill);
            text.setColor(Color.WHITE);
            text.setTextSize(8f);
            text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            for (int c = 0; c < t.columns.length; c++) {
                if (right(c)) {
                    text.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(t.columns[c], colX[c + 1] - 8, y + 13, text);
                } else {
                    text.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(t.columns[c], colX[c] + 8, y + 13, text);
                }
            }
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(android.graphics.Typeface.DEFAULT);
            y += 18;
        }

        boolean right(int c) {
            return t.rightAlign != null && c < t.rightAlign.length && t.rightAlign[c];
        }

        void addRow(String[] row, boolean zebra) {
            if (y + rowH > contentBottom) newPage(false);
            if (zebra) {
                fill.setColor(EMERALD_50);
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, fill);
            }
            text.setColor(INK);
            text.setTextSize(8f);
            text.setTypeface(android.graphics.Typeface.DEFAULT);
            for (int c = 0; c < Math.min(row.length, t.columns.length); c++) {
                String cell = row[c] != null ? row[c] : "";
                if (right(c)) {
                    text.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(cell, colX[c + 1] - 8, y + 12, text);
                } else {
                    text.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(cell, colX[c] + 8, y + 12, text);
                }
            }
            text.setTextAlign(Paint.Align.LEFT);
            y += rowH;
        }

        void addSummaryLines() {
            if (t.summary == null || t.summary.length == 0) return;
            if (y + t.summary.length * 15 + 12 > contentBottom) newPage(false);
            y += 10;
            for (String s : t.summary) {
                text.setTextSize(9f);
                text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
                text.setColor(EMERALD_TEXT);
                text.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(s, PAGE_W - MARGIN, y, text);
                text.setTextAlign(Paint.Align.LEFT);
                y += 15;
            }
            text.setTypeface(android.graphics.Typeface.DEFAULT);
        }

        void drawFooter() {
            fill.setColor(EMERALD_900);
            canvas.drawRect(0, PAGE_H - 20, PAGE_W, PAGE_H, fill);
            text.setColor(Color.WHITE);
            text.setTextSize(7f);
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("DairyFlow — Smart Milk Business Manager   •   Page " + pageNo,
                    PAGE_W / 2f, PAGE_H - 8, text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        PdfDocument finish() {
            if (page != null) {
                drawFooter();
                doc.finishPage(page);
                page = null;
            }
            return doc;
        }
    }

    public static File generate(android.content.Context ctx, Table t) throws IOException {
        Session session = new Session(t);
        boolean zebra = false;
        for (String[] row : t.rows) {
            zebra = !zebra;
            session.addRow(row, zebra);
        }
        session.addSummaryLines();
        PdfDocument doc = session.finish();
        String safe = t.title.replaceAll("[^A-Za-z0-9_-]", "_");
        File out = new File(reportDirectory(ctx), safe + "_" + System.currentTimeMillis() + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            doc.writeTo(fos);
        } finally {
            doc.close();
        }
        return out;
    }
}
