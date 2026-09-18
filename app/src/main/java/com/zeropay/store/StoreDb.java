package com.zeropay.store;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.util.*;

public class StoreDb extends SQLiteOpenHelper {
    public StoreDb(Context context) { super(context, "zeropay.db", null, 4); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products(barcode TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, unit TEXT NOT NULL, price INTEGER NOT NULL CHECK(price>=0), cost INTEGER NOT NULL CHECK(cost>=0), stock INTEGER NOT NULL CHECK(stock>=0), min_stock INTEGER NOT NULL CHECK(min_stock>=0), deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE sales(id TEXT PRIMARY KEY, time INTEGER NOT NULL, total INTEGER NOT NULL, paid INTEGER NOT NULL, method TEXT NOT NULL, refunded INTEGER NOT NULL DEFAULT 0, subtotal INTEGER NOT NULL DEFAULT 0, discount INTEGER NOT NULL DEFAULT 0, promotion TEXT NOT NULL DEFAULT '无促销')");
        db.execSQL("CREATE TABLE sale_items(id INTEGER PRIMARY KEY, sale_id TEXT NOT NULL REFERENCES sales(id), barcode TEXT NOT NULL REFERENCES products(barcode), name TEXT NOT NULL, price INTEGER NOT NULL, cost INTEGER NOT NULL, qty INTEGER NOT NULL CHECK(qty>0))");
        db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY, time INTEGER NOT NULL, barcode TEXT NOT NULL REFERENCES products(barcode), delta INTEGER NOT NULL, balance INTEGER NOT NULL, reason TEXT NOT NULL)");
        db.execSQL("CREATE INDEX movement_time ON movements(time)");
        db.execSQL("CREATE INDEX sale_time ON sales(time)");
        createBundles(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2) {
            db.execSQL("ALTER TABLE sales ADD COLUMN subtotal INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sales ADD COLUMN discount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sales ADD COLUMN promotion TEXT NOT NULL DEFAULT '无促销'");
            db.execSQL("UPDATE sales SET subtotal=total");
        }
        if(oldVersion<3) createBundles(db);
        if(oldVersion<4) db.execSQL("ALTER TABLE products ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
    }
    private void createBundles(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bundles(id TEXT PRIMARY KEY,name TEXT NOT NULL,price INTEGER NOT NULL CHECK(price>=0))");
        db.execSQL("CREATE TABLE IF NOT EXISTS bundle_items(bundle_id TEXT NOT NULL REFERENCES bundles(id) ON DELETE CASCADE,barcode TEXT NOT NULL REFERENCES products(barcode),qty INTEGER NOT NULL CHECK(qty>0),PRIMARY KEY(bundle_id,barcode))");
    }
    public BundleOffer bundle(String id) {
        List<String[]> rows=rows("SELECT name,price FROM bundles WHERE id=?",id);
        if(rows.isEmpty())return null;
        Map<String,Integer> items=new LinkedHashMap<>();
        for(String[] r:rows("SELECT barcode,qty FROM bundle_items WHERE bundle_id=? ORDER BY barcode",id))items.put(r[0],Integer.parseInt(r[1]));
        return new BundleOffer(id,rows.get(0)[0],Long.parseLong(rows.get(0)[1]),items);
    }
    public List<BundleOffer> bundles() {
        List<BundleOffer> result=new ArrayList<>();
        for(String[] r:rows("SELECT id FROM bundles ORDER BY name"))result.add(bundle(r[0]));
        return result;
    }
    public void saveBundle(BundleOffer b) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(String code:b.items.keySet())if(find(code)==null)throw new IllegalArgumentException("商品不存在："+code);
            ContentValues v=new ContentValues();v.put("name",b.name);v.put("price",b.price);
            if(db.update("bundles",v,"id=?",new String[]{b.id})==0){v.put("id",b.id);db.insertOrThrow("bundles",null,v);}
            db.delete("bundle_items","bundle_id=?",new String[]{b.id});
            for(Map.Entry<String,Integer> e:b.items.entrySet()){
                ContentValues item=new ContentValues();item.put("bundle_id",b.id);item.put("barcode",e.getKey());item.put("qty",e.getValue());db.insertOrThrow("bundle_items",null,item);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void deleteBundle(String id){getWritableDatabase().delete("bundles","id=?",new String[]{id});}
    public String bundleContents(BundleOffer b) {
        StringBuilder s=new StringBuilder();
        for(Map.Entry<String,Integer> e:b.items.entrySet()){
            if(s.length()>0)s.append("、");
            Product p=find(e.getKey());s.append(p==null?e.getKey():p.name).append(" [").append(e.getKey()).append("] × ").append(e.getValue());
        }return s.toString();
    }
    public Product cartProduct(String key) {
        if(!key.startsWith("@"))return find(key);
        BundleOffer b=bundle(key);if(b==null)return null;
        int stock=1000000;long cost=0;
        for(Map.Entry<String,Integer> e:b.items.entrySet()){
            Product p=find(e.getKey());if(p==null)return null;
            stock=Math.min(stock,p.stock/e.getValue());cost+=p.cost*e.getValue();
        }
        Product p=new Product(key.substring(1),b.name,"套餐","套",b.price,0,stock,0);
        p.barcode=key;p.cost=cost;return p;
    }
    private Map<String,Integer> components(Map<String,Integer> cart) {
        Map<String,Integer> result=new LinkedHashMap<>();
        for(Map.Entry<String,Integer> e:cart.entrySet()){
            if(e.getValue()==null||e.getValue()<1||e.getValue()>1000000)throw new IllegalArgumentException("购买数量须为 1–1000000");
            Map<String,Integer> parts;
            if(e.getKey().startsWith("@")){
                BundleOffer b=bundle(e.getKey());if(b==null)throw new IllegalArgumentException("套餐已删除，请移除后重选");parts=b.items;
            }else parts=Collections.singletonMap(e.getKey(),1);
            for(Map.Entry<String,Integer> part:parts.entrySet()){
                long qty=(long)part.getValue()*e.getValue()+result.getOrDefault(part.getKey(),0);
                Product p=find(part.getKey());
                if(p==null||qty>p.stock)throw new IllegalArgumentException((p==null?part.getKey():p.name)+" 库存不足（含套餐及单品合计）");
                result.put(part.getKey(),(int)qty);
            }
        }return result;
    }
    public String bundleSnapshot(Map<String,Integer> cart) {
        StringBuilder s=new StringBuilder();
        for(Map.Entry<String,Integer> e:cart.entrySet())if(e.getKey().startsWith("@")){
            BundleOffer b=bundle(e.getKey());if(b==null)throw new IllegalArgumentException("套餐已删除，请移除后重选");
            s.append("；套餐：").append(b.name).append(" × ").append(e.getValue()).append(" 套，单套 ¥").append(Product.money(b.price)).append("（").append(bundleContents(b)).append("）");
        }return s.toString();
    }
    private Product product(Cursor c) { return new Product(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getInt(6),c.getInt(7)); }
    public Product find(String barcode) {
        return lookup(barcode,false);
    }
    private Product lookup(String barcode,boolean includeDeleted) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM products WHERE barcode=?"+(includeDeleted?"":" AND deleted=0"),new String[]{barcode})) { return c.moveToFirst()?product(c):null; }
    }
    public List<Product> products(String query,boolean low) {
        List<Product> result=new ArrayList<>();
        String term="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM products WHERE deleted=0 AND (barcode LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\' OR category LIKE ? ESCAPE '\\')"+(low?" AND stock<=min_stock":"")+" ORDER BY name COLLATE LOCALIZED",new String[]{term,term,term})) {
            while(c.moveToNext()) result.add(product(c));
        } return result;
    }
    public void save(Product p,boolean creating) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Product old=lookup(p.barcode,true);
            if (creating && find(p.barcode)!=null) throw new IllegalArgumentException("该条码已存在，请编辑现有商品");
            if (!creating && find(p.barcode)==null) throw new IllegalArgumentException("商品不存在");
            // Edits never replace inventory: inventory is changed only through a movement.
            if (old!=null) p.stock=old.stock;
            upsert(p,old,"初始库存"); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void deleteProduct(String barcode) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(find(barcode)==null)throw new IllegalArgumentException("商品不存在");
            if(!rows("SELECT bundle_id FROM bundle_items WHERE barcode=?",barcode).isEmpty())
                throw new IllegalArgumentException("该商品用于套餐，请先在套餐管理中移除该商品或删除相关套餐");
            db.execSQL("UPDATE products SET deleted=1 WHERE barcode=?",new Object[]{barcode});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private void upsert(Product p,Product old,String reason) {
        p.validate(); SQLiteDatabase db=getWritableDatabase(); ContentValues v=new ContentValues();
        v.put("deleted",0); v.put("barcode",p.barcode); v.put("name",p.name); v.put("category",p.category); v.put("unit",p.unit);
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
                Product old=lookup(p.barcode,true);
                Product copy=new Product(p.barcode,p.name,p.category,p.unit,p.price,p.cost,
                    old!=null&&!overwriteStock?old.stock:p.stock,p.minimum);
                upsert(copy,old,"CSV 导入");
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public long total(Map<String,Integer> cart) {
        components(cart);
        long sum=0;
        for(Map.Entry<String,Integer> e:cart.entrySet()) {
            Product p=cartProduct(e.getKey()); int qty=e.getValue();
            if(p==null) throw new IllegalArgumentException("商品不存在："+e.getKey());
            if(qty<1 || qty>p.stock) throw new IllegalArgumentException(p.name+" 库存不足（剩余 "+p.stock+"）");
            sum=Math.addExact(sum,Math.multiplyExact(p.price,(long)qty));
        } return sum;
    }
    public String checkout(Map<String,Integer> cart,long expectedTotal,long paid,String method) {
        return checkout(cart,expectedTotal,paid,method,Promotion.NONE);
    }
    public String checkout(Map<String,Integer> cart,long expectedSubtotal,long paid,String method,Promotion promotion) {
        return checkout(cart,expectedSubtotal,paid,method,promotion,null);
    }
    public String checkout(Map<String,Integer> cart,long expectedSubtotal,long paid,String method,Promotion promotion,String expectedBundles) {
        if(cart.isEmpty()) throw new IllegalArgumentException("购物车为空");
        if(!Arrays.asList("现金","微信（已收款）","支付宝（已收款）","其他（已收款）").contains(method)) throw new IllegalArgumentException("收款方式无效");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            long subtotal=total(cart), total=promotion.total(subtotal);
            String bundles=bundleSnapshot(cart);
            if(expectedBundles!=null&&!expectedBundles.equals(bundles))throw new IllegalArgumentException("套餐已变更，请重新结账");
            if(subtotal!=expectedSubtotal) throw new IllegalArgumentException("商品价格已变更，请重新结账");
            if(paid<total) throw new IllegalArgumentException("实收金额不足");
            String id=UUID.randomUUID().toString(); ContentValues sale=new ContentValues();
            sale.put("id",id); sale.put("time",System.currentTimeMillis()); sale.put("total",total); sale.put("paid",paid); sale.put("method",method);
            sale.put("subtotal",subtotal); sale.put("discount",subtotal-total); sale.put("promotion",promotion.description()+bundles);
            db.insertOrThrow("sales",null,sale);
            for(Map.Entry<String,Integer> e:components(cart).entrySet()) {
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
                    Product p=lookup(c.getString(0),true); int qty=c.getInt(1);
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
