package com.zeropay.store;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;

/** Runs only on a dedicated test device: seeds explicit test products. */
@RunWith(AndroidJUnit4.class)
public class DeviceSmokeTest {
    private void navigate(MainActivity a,String tab){
        View label=find(a.getWindow().getDecorView(),tab);
        while(label!=null&&!label.isClickable())label=label.getParent() instanceof View?(View)label.getParent():null;
        assertNotNull(label);label.performClick();
    }
    private TextView find(View v,String s){
        if(v instanceof TextView&&((TextView)v).getText().toString().equals(s))return (TextView)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView r=find(g.getChildAt(i),s);if(r!=null)return r;}}
        return null;
    }
    private EditText field(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText r=field(g.getChildAt(i));if(r!=null)return r;}}return null;}
    private void shot(Instrumentation i,MainActivity a,String name)throws Exception{
        i.waitForIdleSync();i.getUiAutomation().waitForIdle(500,5000);
        Bitmap b=i.getUiAutomation().takeScreenshot();assertNotNull(b);
        File dir=new File(a.getExternalFilesDir(null),"screenshots");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
    @Test public void screensAndBarcodeEntryOnAndroid()throws Exception{
        Instrumentation i=InstrumentationRegistry.getInstrumentation();
        try(StoreDb db=new StoreDb(i.getTargetContext())){
            if(db.find("TEST001")==null)db.save(new Product("TEST001","茉莉花茶 500ml","茶饮","瓶",450,250,36,8),true);
            if(db.find("TEST002")==null)db.save(new Product("TEST002","原味苏打饼干","零食","袋",680,350,3,5),true);
        }
        MainActivity a=(MainActivity)i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            i.runOnMainSync(()->{EditText e=field(a.getWindow().getDecorView());e.setText("TEST001");find(a.getWindow().getDecorView(),"添加条码").performClick();});
            i.waitForIdleSync();i.runOnMainSync(()->assertNotNull(find(a.getWindow().getDecorView(),"茉莉花茶 500ml")));shot(i,a,"01-cashier");
            String[] names={"02-inventory","03-history","04-data"};String[] tabs={"库存","流水","数据"};
            for(int n=0;n<tabs.length;n++){final String tab=tabs[n];i.runOnMainSync(()->navigate(a,tab));shot(i,a,names[n]);}
        }finally{i.runOnMainSync(a::finish);}
    }
}
