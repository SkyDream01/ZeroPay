package com.zeropay.store;

import java.io.*;
import java.util.*;

/** RFC 4180 CSV: quoted delimiters, escaped quotes, multiline cells and UTF-8 BOM. */
public final class Csv {
    public static final String[] HEADER = {"barcode","name","category","unit","price","cost","stock","min_stock"};
    public static List<List<String>> read(Reader source) throws IOException {
        PushbackReader reader = new PushbackReader(source, 1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false, closed = false, start = true, first = true;
        int c, chars = 0;
        while ((c = reader.read()) != -1) {
            if (++chars > 10_000_000) throw new IOException("CSV 超过 10 MB 字符限制");
            if (first && c == 0xFEFF) { first = false; continue; }
            first = false;
            if (quoted) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') cell.append('"');
                    else { quoted = false; closed = true; if (next != -1) reader.unread(next); }
                } else cell.append((char)c);
            } else if (c == ',' || c == '\n' || c == '\r') {
                row.add(cell.toString()); cell.setLength(0); start = true; closed = false;
                if (c != ',') {
                    if (c == '\r') { int n = reader.read(); if (n != '\n' && n != -1) reader.unread(n); }
                    rows.add(row); row = new ArrayList<>();
                    if (rows.size() > 50001) throw new IOException("最多导入 50000 行商品");
                }
            } else if (c == '"') {
                if (!start || closed) throw new IOException("CSV 引号格式错误，第 " + (rows.size()+1) + " 条记录");
                quoted = true; start = false;
            } else {
                if (closed) throw new IOException("CSV 引号后有多余字符，第 " + (rows.size()+1) + " 条记录");
                cell.append((char)c); start = false;
            }
            if (cell.length() > 10000 || row.size() > 50) throw new IOException("CSV 单元格或列数过大");
        }
        if (quoted) throw new IOException("CSV 引号未闭合");
        if (!row.isEmpty() || cell.length() > 0 || closed) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }
    // Prefix risky spreadsheet cells. A leading apostrophe is doubled for lossless round trips.
    public static String safe(String s) {
        if (s.isEmpty()) return s;
        int offset=0;
        while(offset<s.length() && Character.isWhitespace(s.charAt(offset))) offset++;
        String t = s.substring(offset);
        if (s.startsWith("'") || (!t.isEmpty() && "=+-@\t\r\n".indexOf(t.charAt(0)) >= 0) || "\t\r\n".indexOf(s.charAt(0)) >= 0) return "'" + s;
        return s;
    }
    public static String restore(String s) {
        if (!s.startsWith("'")) return s;
        String tail = s.substring(1);
        return safe(tail).equals(s) ? tail : s;
    }
    public static void row(Writer writer, Object... values) throws IOException {
        for (int i=0; i<values.length; i++) {
            if (i>0) writer.write(',');
            String text = values[i] == null ? "" : values[i].toString();
            if (values[i] instanceof String) text = safe(text);
            writer.write('"'); writer.write(text.replace("\"", "\"\"")); writer.write('"');
        }
        writer.write("\r\n");
    }
    public static List<Product> products(Reader reader) throws IOException {
        List<List<String>> rows = read(reader);
        if (rows.isEmpty()) throw new IOException("文件为空");
        if (!rows.get(0).equals(Arrays.asList(HEADER))) throw new IOException("表头不匹配，请使用应用导出的商品模板");
        List<Product> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (int i=1; i<rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.size()==1 && r.get(0).trim().isEmpty()) continue;
            try {
                if (r.size()!=8) throw new IllegalArgumentException("应为 8 列，实际 " + r.size() + " 列");
                String barcode = r.get(0).trim();
                if (!seen.add(barcode)) throw new IllegalArgumentException("重复条码：" + barcode);
                result.add(new Product(barcode, restore(r.get(1)), restore(r.get(2)), restore(r.get(3)),
                    Product.cents(r.get(4)), Product.cents(r.get(5)), Product.quantity(r.get(6)), Product.quantity(r.get(7))));
                if(result.size()>50000) throw new IllegalArgumentException("最多导入 50000 行商品");
            } catch (IllegalArgumentException e) { throw new IOException("第 " + (i+1) + " 条记录：" + e.getMessage()); }
        }
        if (result.isEmpty()) throw new IOException("CSV 没有商品记录");
        return result;
    }
    public static void product(Writer w, Product p) throws IOException {
        row(w, p.barcode, p.name, p.category, p.unit, Product.money(p.price), Product.money(p.cost), p.stock, p.minimum);
    }
}
