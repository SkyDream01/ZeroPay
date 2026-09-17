package com.zeropay.store;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

public class CsvTest {
    private String csv(String... rows){return String.join(",",Csv.HEADER)+"\r\n"+String.join("\r\n",rows);}
    @Test public void preservesLeadingZerosAndQuotedNewlines()throws Exception{
        List<Product> p=Csv.products(new StringReader("\uFEFF"+csv("00123,\"茶,\"\"绿\"\"\n饮料\",饮料,瓶,0.10,0.01,12,3")));
        assertEquals("00123",p.get(0).barcode);assertEquals("茶,\"绿\"\n饮料",p.get(0).name);assertEquals(10,p.get(0).price);
    }
    @Test public void roundTripProtectsFormulas()throws Exception{
        for(String name:new String[]{"=SUM(A1)"," +cmd","@test","'literal","正常,\"名称\"\n第二行"}){
            Product p=new Product("00123",name,"饮料","件",1234,111,3,2);StringWriter w=new StringWriter();Csv.row(w,(Object[])Csv.HEADER);Csv.product(w,p);
            assertEquals(name,Csv.products(new StringReader(w.toString())).get(0).name);
        }
        assertEquals("'=1+2",Csv.safe("=1+2"));
    }
    @Test public void refusesDuplicateBarcode()throws Exception{assertThrows(IOException.class,()->Csv.products(new StringReader(csv("001,茶,饮料,瓶,1,0,1,0","001,水,饮料,瓶,1,0,1,0"))));}
    @Test public void refusesMalformedCsv(){for(String s:new String[]{"a,\"b","a,\"b\"x","a,b\"c"})assertThrows(IOException.class,()->Csv.read(new StringReader(s)));}
    @Test public void refusesWrongHeaderAndNegativeInventory(){assertThrows(IOException.class,()->Csv.products(new StringReader("name,barcode\nx,y")));assertThrows(IOException.class,()->Csv.products(new StringReader(csv("001,茶,饮料,瓶,1,0,-1,0"))));}
    @Test public void moneyIsExact(){assertEquals(29,Product.cents("0.29"));assertEquals("0.10",Product.money(10));for(String s:new String[]{"-1","1.001","NaN","1e2","9223372036854775808"})assertThrows(IllegalArgumentException.class,()->Product.cents(s));}
}
