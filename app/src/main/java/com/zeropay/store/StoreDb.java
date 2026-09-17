package com.zeropay.store;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.util.*;

public class StoreDb extends SQLiteOpenHelper {
    public StoreDb(Context context) { super(context, "zeropay.db", null, 2); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products(barcode TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, unit TEXT NOT NULL, price INTEGER NOT NULL CHECK(price>=0), cost INTEGER NOT NULL CHECK(cost>=0), stock INTEGER NOT NULL CHECK(stock>=0), min_stock INTEGER NOT NULL CHECK(min_stock>=0))");
        db.execSQL("CREATE TABLE sales(id TEXT PRIMARY KEY, time INTEGER NOT NULL, total INTEGER NOT NULL, paid INTEGER NOT NULL, method TEXT NOT NULL, refunded INTEGER NOT NULL DEFAULT 0, subtotal INTEGER NOT NULL DEFAULT 0, discount INTEGER NOT NULL DEFAULT 0, promotion TEXT NOT NULL DEFAULT '无促销')");
        db.execSQL("CREATE TABLE sale_items(id INTEGER PRIMARY KEY, sale_id TEXT NOT NULL REFERENCES sales(id), barcode TEXT NOT NULL REFERENCES products(barcode), name TEXT NOT NULL, price INTEGER NOT NULL, cost INTEGER NOT NULL, qty INTEGER NOT NULL CHECK(qty>0))");
        db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY, time INTEGER NOT NULL, barcode TEXT NOT NULL REFERENCES products(barcode), delta INTEGER NOT NULL, balance INTEGER NOT NULL, reason TEXT NOT NULL)");
        db.execSQL("CREATE INDEX movement_time ON movements(time)");
        db.execSQL("CREATE INDEX sale_time ON sales(time)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2) {
            db.execSQL("ALTER TABLE sales ADD COLUMN subtotal INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sales ADD COLUMN discount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sales ADD COLUMN promotion TEXT NOT NULL DEFAULT '无促销'");
            db.execSQL("UPDATE sales SET subtotal=total");
        }
    }
    private Product product(Cursor c) { return new Product(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getInt(6),c.getInt(7)); }
    public Product find(String barcode) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM products WHERE barcode=?",new String[]{barcode})) { return c.moveToFirst()?product(c):null; }
    }
    public List<Product> products(String query,boolean low) {
        List<Product> result=new ArrayList<>();
        String term="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM products WHERE (barcode LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\' OR category LIKE ? ESCAPE '\\')"+(low?" AND stock<=min_stock":"")+" ORDER BY name COLLATE LOCALIZED",new String[]{term,term,term})) {
            while(c.moveToNext()) result.add(product(c));
        } return result;
    }
    public void save(Product p,boolean creating) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Product old=find(p.barcode);
            if (creating && old!=null) throw new IllegalArgumentException("该条码已存在，请编辑现有商品");
            if (!creating && old==null) throw new IllegalArgumentException("商品不存在");
            // Edits never replace inventory: inventory is changed only through a movement.
            if (old!=null) p.stock=old.stock;
            upsert(p,old,"初始库存"); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void upsert(Product p,Product old,String reason) {
        p.validate(); SQLiteDatabase db=getWritableDatabase(); ContentValues v=new ContentValues();
        v.put("barcode",p.barcode); v.put("name",p.name); v.put("category",p.category); v.put("unit",p.unit);
        v.put("price",p.price); v.put("cost",p.cost); v.put("stock",p.stock); v.put("min_stock",p.minimum);
        if(old==null) db.insertOrThrow("products",null,v); else db.update("products",v,"barcode=?",new String[]{p.barcode});
        int delta=p.stock-(old==null?0:old.stock);
        if(delta!=0) movement(p.barcode,delta,p.stock,reason);
    }
    private void movement(String barcode,int delta,int balance,String reason) {
        ContentValues v=new ContentValues(); v.put("time",System.currentTimeMillis()); v.put("barcode",barcode);
        v.put("delta",delta); v.put("balance",balance); v.put("reason",reason);
        getWritableDatabase().insertOrThrow("movements",null,v);
    }
    public void adjust(String barcode,int quantity,boolean absolute,String reason) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Product p=find(barcode); if(p==null) throw new IllegalArgumentException("商品不存在");
            int next=absolute?quantity:Math.addExact(p.stock,quantity);
            if(next<0 || next>1000000) throw new IllegalArgumentException("变更后库存须在 0–1000000 之间");
            if(reason.trim().isEmpty()) throw new IllegalArgumentException("请填写变更原因");
            db.execSQL("UPDATE products SET stock=? WHERE barcode=?",new Object[]{next,barcode});
            if(next!=p.stock) movement(barcode,next-p.stock,next,reason);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void importProducts(List<Product> products,boolean overwriteStock) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Set<String> seen=new HashSet<>();
            for(Product p:products) {
                if(!seen.add(p.barcode)) throw new IllegalArgumentException("重复条码："+p.barcode);
                Product old=find(p.barcode);
                Product copy=new Product(p.barcode,p.name,p.category,p.unit,p.price,p.cost,
                    old!=null&&!overwriteStock?old.stock:p.stock,p.minimum);
                upsert(copy,old,"CSV 导入");
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public long total(Map<String,Integer> cart) {
        long sum=0;
        for(Map.Entry<String,Integer> e:cart.entrySet()) {
            Product p=find(e.getKey()); int qty=e.getValue();
            if(p==null) throw new IllegalArgumentException("商品不存在："+e.getKey());
            if(qty<1 || qty>p.stock) throw new IllegalArgumentException(p.name+" 库存不足（剩余 "+p.stock+"）");
            sum=Math.addExact(sum,Math.multiplyExact(p.price,(long)qty));
        } return sum;
    }
    public String checkout(Map<String,Integer> cart,long expectedTotal,long paid,String method) {
        return checkout(cart,expectedTotal,paid,method,Promotion.NONE);
    }
    public String checkout(Map<String,Integer> cart,long expectedSubtotal,long paid,String method,Promotion promotion) {
        if(cart.isEmpty()) throw new IllegalArgumentException("购物车为空");
        if(!Arrays.asList("现金","微信（已收款）","支付宝（已收款）","其他（已收款）").contains(method)) throw new IllegalArgumentException("收款方式无效");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            long subtotal=total(cart), total=promotion.total(subtotal);
            if(subtotal!=expectedSubtotal) throw new IllegalArgumentException("商品价格已变更，请重新结账");
            if(paid<total) throw new IllegalArgumentException("实收金额不足");
            String id=UUID.randomUUID().toString(); ContentValues sale=new ContentValues();
            sale.put("id",id); sale.put("time",System.currentTimeMillis()); sale.put("total",total); sale.put("paid",paid); sale.put("method",method);
            sale.put("subtotal",subtotal); sale.put("discount",subtotal-total); sale.put("promotion",promotion.description());
            db.insertOrThrow("sales",null,sale);
            for(Map.Entry<String,Integer> e:cart.entrySet()) {
                Product p=find(e.getKey()); int qty=e.getValue(); ContentValues v=new ContentValues();
                v.put("sale_id",id); v.put("barcode",p.barcode); v.put("name",p.name); v.put("price",p.price); v.put("cost",p.cost); v.put("qty",qty);
                db.insertOrThrow("sale_items",null,v);
                db.execSQL("UPDATE products SET stock=stock-? WHERE barcode=?",new Object[]{qty,p.barcode});
                movement(p.barcode,-qty,p.stock-qty,"销售 "+id);
            }
            db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }
    public void refund(String id) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            try(Cursor c=db.rawQuery("SELECT refunded FROM sales WHERE id=?",new String[]{id})) {
                if(!c.moveToFirst()||c.getInt(0)!=0) throw new IllegalArgumentException("订单不存在或已退货");
            }
            try(Cursor c=db.rawQuery("SELECT barcode,qty FROM sale_items WHERE sale_id=?",new String[]{id})) {
                while(c.moveToNext()) {
                    Product p=find(c.getString(0)); int qty=c.getInt(1);
                    if((long)p.stock+qty>1000000) throw new IllegalArgumentException("退货后库存超过上限");
                    db.execSQL("UPDATE products SET stock=stock+? WHERE barcode=?",new Object[]{qty,p.barcode});
                    movement(p.barcode,qty,p.stock+qty,"整单退货 "+id);
                }
            }
            db.execSQL("UPDATE sales SET refunded=1 WHERE id=?",new Object[]{id}); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public long scalar(String sql) { try(Cursor c=getReadableDatabase().rawQuery(sql,null)) { return c.moveToFirst()?c.getLong(0):0; } }
    public List<String[]> rows(String sql,String... args) {
        List<String[]> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)) { while(c.moveToNext()) { String[] r=new String[c.getColumnCount()]; for(int i=0;i<r.length;i++) r[i]=c.getString(i); rows.add(r); } }
        return rows;
    }
    public void export(Writer writer,String kind) throws IOException {
        SQLiteDatabase db=getReadableDatabase(); db.beginTransaction();
        try {
            writer.write('\uFEFF');
            if(kind.equals("products")||kind.equals("template")) {
                Csv.row(writer,(Object[])Csv.HEADER);
                if(kind.equals("products")) for(Product p:products("",false)) Csv.product(writer,p);
                else Csv.product(writer,new Product("6901234567892","示例矿泉水","饮料","瓶",200,100,24,5));
            } else {
                String sql;
                if(kind.equals("sales")) {
                    Csv.row(writer,"order_id","time_local","payment","status","total","paid","change","barcode","name","quantity","unit_price","unit_cost","line_total","subtotal","discount","promotion");
                    sql="SELECT s.id,strftime('%Y-%m-%d %H:%M:%S',s.time/1000,'unixepoch','localtime'),s.method,CASE s.refunded WHEN 0 THEN '已完成' ELSE '已退货' END,s.total,s.paid,s.paid-s.total,i.barcode,i.name,i.qty,i.price,i.cost,i.price*i.qty,s.subtotal,s.discount,s.promotion FROM sales s JOIN sale_items i ON i.sale_id=s.id ORDER BY s.time,i.id";
                } else {
                    Csv.row(writer,"movement_id","time_local","barcode","name","quantity_change","stock_after","reason");
                    sql="SELECT m.id,strftime('%Y-%m-%d %H:%M:%S',m.time/1000,'unixepoch','localtime'),m.barcode,p.name,m.delta,m.balance,m.reason FROM movements m JOIN products p ON p.barcode=m.barcode ORDER BY m.id";
                }
                try(Cursor c=db.rawQuery(sql,null)) {
                    while(c.moveToNext()) {
                        Object[] values=new Object[c.getColumnCount()];
                        for(int i=0;i<values.length;i++) {
                            if(kind.equals("sales")&&(i==4||i==5||i==6||(i>=10&&i<=14))) values[i]=Product.money(c.getLong(i));
                            else if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER) values[i]=c.getLong(i);
                            else values[i]=c.getString(i);
                        } Csv.row(writer,values);
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
