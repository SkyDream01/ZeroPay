package com.zeropay.store;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class BundleTest {
    private StoreDb db;
    @Before public void setup(){
        RuntimeEnvironment.getApplication().deleteDatabase("zeropay.db");db=new StoreDb(RuntimeEnvironment.getApplication());
        db.save(new Product("A","茶","","瓶",500,100,10,0),true);
        db.save(new Product("B","面包","","个",800,200,5,0),true);
        db.saveBundle(new BundleOffer("@meal","早餐",1200,Map.of("A",2,"B",1)));
    }
    @After public void close(){db.close();}
    @Test public void mixedCartUsesBundlePriceAndRefundsComponents()throws Exception{
        Map<String,Integer> cart=Map.of("@meal",2,"A",1);
        assertEquals(5,db.cartProduct("@meal").stock);assertEquals(2900,db.total(cart));
        String id=db.checkout(cart,2900,3000,"现金",Promotion.NONE,db.bundleSnapshot(cart));
        assertEquals(5,db.find("A").stock);assertEquals(3,db.find("B").stock);
        assertEquals(2900,db.scalar("SELECT total FROM sales"));
        assertEquals(2,db.scalar("SELECT count(*) FROM sale_items"));
        db.saveBundle(new BundleOffer("@meal","新套餐",200,Map.of("B",4)));
        db.deleteBundle("@meal");
        StringWriter out=new StringWriter();db.export(out,"sales");
        assertTrue(out.toString().contains("早餐 × 2 套，单套 ¥12.00"));assertTrue(out.toString().contains("茶 [A] × 2"));
        db.refund(id);assertEquals(10,db.find("A").stock);assertEquals(5,db.find("B").stock);
        assertThrows(IllegalArgumentException.class,()->db.refund(id));
    }
    @Test public void sharedComponentsAreCheckedAcrossBundlesAndSingles(){
        db.saveBundle(new BundleOffer("@tea","茶包",100,Map.of("A",3)));
        assertThrows(IllegalArgumentException.class,()->db.total(Map.of("@meal",4,"@tea",1)));
        assertThrows(IllegalArgumentException.class,()->db.checkout(Map.of("@meal",5,"A",1),6500,6500,"现金"));
        assertEquals(10,db.find("A").stock);assertEquals(0,db.scalar("SELECT count(*) FROM sales"));
    }
    @Test public void changedDefinitionSamePriceAndUnderpaymentAreRejected(){
        Map<String,Integer> cart=Map.of("@meal",1);String snapshot=db.bundleSnapshot(cart);
        assertThrows(IllegalArgumentException.class,()->db.checkout(cart,1200,1199,"现金"));
        db.saveBundle(new BundleOffer("@meal","早餐",1200,Map.of("A",1,"B",1)));
        assertThrows(IllegalArgumentException.class,()->db.checkout(cart,1200,1200,"现金",Promotion.NONE,snapshot));
        assertEquals(0,db.scalar("SELECT count(*) FROM sales"));
    }
    @Test public void bundleSupportsPromotionZeroPriceAndPersistence(){
        db.close();db=new StoreDb(RuntimeEnvironment.getApplication());
        assertEquals(2,db.bundle("@meal").items.get("A").intValue());
        db.checkout(Map.of("@meal",1),1200,1020,"现金",Promotion.discount("8.5"));
        assertEquals(1020,db.scalar("SELECT total FROM sales"));
        db.saveBundle(new BundleOffer("@free","赠品",0,Map.of("B",1)));
        assertEquals(0,db.total(Map.of("@free",1)));
        db.checkout(Map.of("@free",1),0,0,"现金");assertEquals(3,db.find("B").stock);
    }
    @Test public void invalidBundleEditIsAtomicAndHugeQuantitiesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new BundleOffer("@x","",1,Map.of("A",1)));
        assertThrows(IllegalArgumentException.class,()->new BundleOffer("@x","套餐",1,Map.of()));
        assertThrows(IllegalArgumentException.class,()->new BundleOffer("@x","套餐",1,Map.of("A",0)));
        assertThrows(IllegalArgumentException.class,()->new BundleOffer("@x","套餐",100000001,Map.of("A",1)));
        assertThrows(IllegalArgumentException.class,()->db.saveBundle(new BundleOffer("@meal","损坏",1,Map.of("missing",1))));
        assertEquals("早餐",db.bundle("@meal").name);
        db.saveBundle(new BundleOffer("@huge","大量",1,Map.of("A",1000000)));
        assertThrows(IllegalArgumentException.class,()->db.total(Map.of("@huge",1000000)));
    }
    @Test public void versionTwoUpgradePreservesExistingSale(){
        String id=db.checkout(Map.of("A",1),500,500,"现金");
        var sql=db.getWritableDatabase();sql.execSQL("DROP TABLE bundle_items");sql.execSQL("DROP TABLE bundles");sql.setVersion(2);
        db.close();db=new StoreDb(RuntimeEnvironment.getApplication());
        assertTrue(db.bundles().isEmpty());assertEquals(500,db.scalar("SELECT total FROM sales"));
        db.refund(id);assertEquals(10,db.find("A").stock);
        db.saveBundle(new BundleOffer("@new","升级套餐",100,Map.of("A",1)));
        assertEquals(100,db.total(Map.of("@new",1)));
    }
}
