package com.zeropay.store;

import java.math.BigDecimal;

public class Product {
    public String barcode, name, category, unit;
    public long price, cost;
    public int stock, minimum;

    public Product(String barcode, String name, String category, String unit, long price, long cost, int stock, int minimum) {
        this.barcode = barcode; this.name = name; this.category = category; this.unit = unit;
        this.price = price; this.cost = cost; this.stock = stock; this.minimum = minimum;
        validate();
    }
    public void validate() {
        if (barcode == null || !barcode.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            throw new IllegalArgumentException("条码须为 1–80 位字母、数字、点、横线或下划线，首位为字母或数字");
        if (name == null || name.trim().isEmpty() || name.length() > 100) throw new IllegalArgumentException("商品名称须为 1–100 个字符");
        if (category == null || category.length() > 50 || unit == null || unit.trim().isEmpty() || unit.length() > 20)
            throw new IllegalArgumentException("分类最多 50 字，单位须为 1–20 字");
        if (price < 0 || cost < 0 || price > 100000000 || cost > 100000000) throw new IllegalArgumentException("金额须在 0–1000000 元之间");
        if (stock < 0 || minimum < 0 || stock > 1000000 || minimum > 1000000) throw new IllegalArgumentException("库存和预警值须在 0–1000000 之间");
    }
    public static long cents(String text) {
        if (!text.trim().matches("[0-9]+(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("金额请输入非负数字，最多两位小数");
        try { return new BigDecimal(text.trim()).movePointRight(2).longValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("金额过大"); }
    }
    public static String money(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }
    public static int quantity(String value) {
        try {
            if (!value.trim().matches("[0-9]+")) throw new NumberFormatException();
            int n = Integer.parseInt(value.trim());
            if (n > 1000000) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("数量须为 0–1000000 的整数"); }
    }
}
