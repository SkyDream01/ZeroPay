package com.zeropay.store;

import java.util.*;

/** A saved fixed-price bundle. Inventory belongs to its component products. */
public final class BundleOffer {
    public final String id, name;
    public final long price;
    public final Map<String,Integer> items;

    public BundleOffer(String id,String name,long price,Map<String,Integer> items) {
        if(id==null || !id.matches("@[A-Za-z0-9-]+")) throw new IllegalArgumentException("套餐编号无效");
        if(name==null || name.trim().isEmpty() || name.length()>100) throw new IllegalArgumentException("套餐名称须为 1–100 个字符");
        if(price<0 || price>100000000) throw new IllegalArgumentException("组合售价须在 0–1000000 元之间");
        if(items==null || items.isEmpty()) throw new IllegalArgumentException("请添加组成商品");
        for(Map.Entry<String,Integer> e:items.entrySet())
            if(e.getKey()==null || e.getKey().startsWith("@") || e.getValue()==null || e.getValue()<1 || e.getValue()>1000000)
                throw new IllegalArgumentException("组成商品数量须为 1–1000000，不支持嵌套套餐");
        this.id=id;this.name=name.trim();this.price=price;
        this.items=Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }
}
