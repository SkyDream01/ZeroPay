package com.zeropay.store;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class StoreDbTest {
    StoreDb db;
    @Before public void setup(){RuntimeEnvironment.getApplication().deleteDatabase("zeropay.db");db=new StoreDb(RuntimeEnvironment.getApplication());db.save(p("001",10),true);}
    @After public void close(){db.close();}
    private Product p(String code,int stock){return new Product(code,"茶","饮料","瓶",29,10,stock,2);}
    @Test public void checkoutIsExactAndRefundIsIdempotent(){Map<String,Integer> cart=Map.of("001",3);String id=db.checkout(cart,87,100,"现金");assertEquals(7,db.find("001").stock);assertEquals(87,db.scalar("SELECT total FROM sales"));db.refund(id);assertEquals(10,db.find("001").stock);assertThrows(IllegalArgumentException.class,()->db.refund(id));assertEquals(10,db.find("001").stock);}
    @Test public void insufficientStockNeverWritesOrder(){db.save(p("002",1),true);Map<String,Integer> cart=new LinkedHashMap<>();cart.put("001",2);cart.put("002",2);assertThrows(IllegalArgumentException.class,()->db.checkout(cart,116,116,"现金"));assertEquals(10,db.find("001").stock);assertEquals(0,db.scalar("SELECT count(*) FROM sales"));}
    @Test public void rejectsUnderpaymentAndChangedPrice(){assertThrows(IllegalArgumentException.class,()->db.checkout(Map.of("001",1),29,28,"现金"));assertThrows(IllegalArgumentException.class,()->db.checkout(Map.of("001",1),30,30,"现金"));assertEquals(10,db.find("001").stock);}
    @Test public void invalidStockChangeRollsBack(){assertThrows(IllegalArgumentException.class,()->db.adjust("001",-11,false,"损耗"));assertEquals(10,db.find("001").stock);db.adjust("001",5,true,"盘点");assertEquals(5,db.find("001").stock);assertEquals(-5,db.scalar("SELECT delta FROM movements ORDER BY id DESC LIMIT 1"));}
    @Test public void importPreservesStockUnlessSelected(){db.importProducts(List.of(p("001",25),p("002",8)),false);assertEquals(10,db.find("001").stock);assertEquals(8,db.find("002").stock);db.importProducts(List.of(p("001",25)),true);assertEquals(25,db.find("001").stock);}
    @Test public void invalidImportIsAtomic(){Product invalid=p("003",2);invalid.price=-1;assertThrows(IllegalArgumentException.class,()->db.importProducts(List.of(p("002",8),invalid),true));assertNull(db.find("002"));assertEquals(1,db.scalar("SELECT count(*) FROM products"));}
    @Test public void duplicateCreationCannotOverwrite(){assertThrows(IllegalArgumentException.class,()->db.save(p("001",99),true));assertEquals(10,db.find("001").stock);}
    @Test public void exportsReimportExactly()throws Exception{StringWriter w=new StringWriter();db.export(w,"products");List<Product> result=Csv.products(new StringReader(w.toString()));assertEquals("001",result.get(0).barcode);assertEquals(29,result.get(0).price);}
    @Test public void refundOverflowRollsBack(){db.save(p("002",1),true);String id=db.checkout(Map.of("001",2,"002",1),87,87,"现金");db.adjust("002",1000000,true,"盘点");assertThrows(IllegalArgumentException.class,()->db.refund(id));assertEquals(8,db.find("001").stock);assertEquals(0,db.scalar("SELECT refunded FROM sales"));}
    @Test public void promotionIsStoredExportedAndRefunded()throws Exception{
        String id=db.checkout(Map.of("001",3),87,100,"现金",Promotion.discount("8.5"));
        assertEquals(74,db.scalar("SELECT total FROM sales"));assertEquals(87,db.scalar("SELECT subtotal FROM sales"));assertEquals(13,db.scalar("SELECT discount FROM sales"));
        StringWriter w=new StringWriter();db.export(w,"sales");List<List<String>> exported=Csv.read(new StringReader(w.toString()));
        assertEquals(List.of("subtotal","discount","promotion"),exported.get(0).subList(13,16));
        assertEquals(List.of("0.87","0.13","8.5 折"),exported.get(1).subList(13,16));
        assertEquals("0.74",exported.get(1).get(4));assertEquals("0.26",exported.get(1).get(6));
        db.refund(id);assertEquals(10,db.find("001").stock);assertEquals(74,db.scalar("SELECT total FROM sales"));
        assertThrows(IllegalArgumentException.class,()->db.refund(id));
    }
    @Test public void promotionStillChecksPriceStockAndPayment(){
        Promotion p=Promotion.discount("8.5");
        assertThrows(IllegalArgumentException.class,()->db.checkout(Map.of("001",1),29,24,"现金",p));
        assertThrows(IllegalArgumentException.class,()->db.checkout(Map.of("001",1),28,25,"现金",p));
        assertThrows(IllegalArgumentException.class,()->db.checkout(Map.of("001",11),319,319,"现金",p));
        assertEquals(0,db.scalar("SELECT count(*) FROM sales"));assertEquals(10,db.find("001").stock);
    }
    @Test public void customPriceIsStoredExportedAndRefunded()throws Exception{
        Promotion p=new Promotion(3,0,50);
        assertThrows(IllegalArgumentException.class,()->db.checkout(Map.of("001",3),87,49,"现金",p));
        String id=db.checkout(Map.of("001",3),87,100,"现金",p);
        assertEquals(50,db.scalar("SELECT total FROM sales"));assertEquals(37,db.scalar("SELECT discount FROM sales"));
        StringWriter w=new StringWriter();db.export(w,"sales");List<List<String>> rows=Csv.read(new StringReader(w.toString()));
        assertEquals(List.of("0.87","0.37","自定义价格 ¥0.50"),rows.get(1).subList(13,16));
        assertEquals("0.50",rows.get(1).get(4));assertEquals("0.50",rows.get(1).get(6));
        db.refund(id);assertEquals(10,db.find("001").stock);
    }
    @Test public void fullReductionAllowsZeroPayment(){
        db.checkout(Map.of("001",1),29,0,"现金",new Promotion(2,29,29));
        assertEquals(0,db.scalar("SELECT total FROM sales"));assertEquals(29,db.scalar("SELECT discount FROM sales"));assertEquals(9,db.find("001").stock);
    }
    @Test public void versionOneUpgradePreservesOrdersAndInventory(){
        android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();
        sql.execSQL("DROP TABLE sales");
        sql.execSQL("CREATE TABLE sales(id TEXT PRIMARY KEY,time INTEGER NOT NULL,total INTEGER NOT NULL,paid INTEGER NOT NULL,method TEXT NOT NULL,refunded INTEGER NOT NULL DEFAULT 0)");
        sql.execSQL("INSERT INTO sales VALUES('legacy',1,87,100,'现金',0)");
        sql.execSQL("INSERT INTO sale_items(sale_id,barcode,name,price,cost,qty) VALUES('legacy','001','茶',29,10,3)");
        sql.setVersion(1);db.close();db=new StoreDb(RuntimeEnvironment.getApplication());
        assertEquals(87,db.scalar("SELECT subtotal FROM sales"));assertEquals(0,db.scalar("SELECT discount FROM sales"));
        assertEquals("无促销",db.rows("SELECT promotion FROM sales").get(0)[0]);assertEquals(10,db.find("001").stock);
        db.refund("legacy");assertEquals(13,db.find("001").stock);
    }
}
