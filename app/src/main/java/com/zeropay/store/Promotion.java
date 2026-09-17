package com.zeropay.store;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable, order-level promotion; all monetary values are cents. */
public final class Promotion {
    public static final Promotion NONE = new Promotion(0, 0, 0);
    public final int type;
    public final long threshold, value;

    public Promotion(int type, long threshold, long value) {
        if (type < 0 || type > 2 || (type == 0 && (threshold != 0 || value != 0))
                || (type == 1 && (threshold != 0 || value < 1 || value > 99))
                || (type == 2 && (threshold < 1 || threshold > 100000000 || value < 1 || value > threshold)))
            throw new IllegalArgumentException("折扣须为 0.1–9.9 折；满减门槛须大于 0 且不超过 1000000 元，减免须大于 0 且不超过门槛");
        this.type = type; this.threshold = threshold; this.value = value;
    }

    public static Promotion discount(String text) {
        try {
            return new Promotion(1, 0, new BigDecimal(text.trim()).movePointRight(1).longValueExact());
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("折扣请输入 0.1–9.9，最多一位小数，例如 8.5 表示八五折");
        }
    }

    public long total(long subtotal) {
        if (subtotal < 0) throw new IllegalArgumentException("商品合计不能为负数");
        if (type == 1) return BigDecimal.valueOf(subtotal).multiply(BigDecimal.valueOf(value))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
        return type == 2 && subtotal >= threshold ? subtotal - value : subtotal;
    }

    public String description() {
        if (type == 1) return BigDecimal.valueOf(value, 1).stripTrailingZeros().toPlainString() + " 折";
        if (type == 2) return "满 ¥" + Product.money(threshold) + " 减 ¥" + Product.money(value);
        return "无促销";
    }
}
