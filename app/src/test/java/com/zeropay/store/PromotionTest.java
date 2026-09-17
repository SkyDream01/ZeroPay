package com.zeropay.store;

import org.junit.Test;
import static org.junit.Assert.*;

public class PromotionTest {
    @Test public void discountRoundsPayableToNearestCent() {
        assertEquals(25,Promotion.discount("8.5").total(29));
        assertEquals(1,Promotion.discount("5").total(1));
        assertEquals(0,Promotion.discount("8.5").total(0));
        assertEquals(8500,Promotion.discount("8.5").total(10000));
        assertEquals(4611686018427387904L,Promotion.discount("5").total(Long.MAX_VALUE));
    }
    @Test public void reductionAppliesOnceOnlyAtThreshold() {
        Promotion p=new Promotion(2,10000,1000);
        assertEquals(9999,p.total(9999));assertEquals(9000,p.total(10000));assertEquals(29000,p.total(30000));
        assertEquals(0,new Promotion(2,10000,10000).total(10000));
    }
    @Test public void invalidRulesAreRejected() {
        for(String rate:new String[]{"0","10","-1","8.55","abc","NaN"})
            assertThrows(IllegalArgumentException.class,()->Promotion.discount(rate));
        assertThrows(IllegalArgumentException.class,()->new Promotion(2,0,1));
        assertThrows(IllegalArgumentException.class,()->new Promotion(2,100,101));
        assertThrows(IllegalArgumentException.class,()->new Promotion(2,100,0));
        assertThrows(IllegalArgumentException.class,()->new Promotion(4,0,0));
        assertThrows(IllegalArgumentException.class,()->new Promotion(3,1,100));
        assertThrows(IllegalArgumentException.class,()->new Promotion(3,0,-1));
        assertThrows(IllegalArgumentException.class,()->new Promotion(3,0,100000001));
    }
    @Test public void customPriceCapsPayableAtOriginalTotal() {
        Promotion p=new Promotion(3,0,Product.cents("12.34"));
        assertEquals(1234,p.total(2000));assertEquals(1234,p.total(3000));
        assertEquals(1000,p.total(1000));assertEquals(0,p.total(0));
        assertEquals(0,new Promotion(3,0,0).total(2000));
        assertEquals(100000000,new Promotion(3,0,100000000).total(Long.MAX_VALUE));
        assertEquals("自定义价格 ¥12.34",p.description());
    }
}
