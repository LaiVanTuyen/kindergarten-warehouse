package com.kindergarten.warehouse.util;

import com.kindergarten.warehouse.entity.AuditLog;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AuditLogCsvExport {

    private final PrintWriter pw;
    private final DateTimeFormatter formatter;

    public AuditLogCsvExport(OutputStream out) {
        try {
            // Write UTF-8 BOM so Excel opens it with correct encoding
            out.write(239);
            out.write(187);
            out.write(191);
        } catch (Exception e) {
        }
        this.pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        this.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    public void writeHeader() {
        pw.println("ID,Timestamp,Action,Username,Target,Detail,IP Address,User Agent");
    }

    public void writeRows(List<AuditLog> logs) {
        for (AuditLog log : logs) {
            pw.print(log.getId() + ",");
            pw.print(escapeCsv(log.getTimestamp() != null ? log.getTimestamp().format(formatter) : "") + ",");
            pw.print(escapeCsv(log.getAction()) + ",");
            pw.print(escapeCsv(log.getUsername()) + ",");
            pw.print(escapeCsv(log.getTarget()) + ",");
            pw.print(escapeCsv(log.getDetail()) + ",");
            pw.print(escapeCsv(log.getIpAddress()) + ",");
            pw.print(escapeCsv(log.getUserAgent()));
            pw.println();
        }
    }

    public void flush() {
        pw.flush();
    }

    private String escapeCsv(String data) {
        if (data == null) {
            return "";
        }
        // Normalize line breaks
        String escapedData = data.replaceAll("\\R", " ");
        // If string contains comma or quote, wrap in double quotes and escape internal
        // quotes
        if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("'")) {
            escapedData = escapedData.replace("\"", "\"\"");
            escapedData = "\"" + escapedData + "\"";
        }
        return escapedData;
    }
}
