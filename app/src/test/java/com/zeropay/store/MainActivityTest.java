package com.zeropay.store;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import android.view.*;
import android.widget.*;
import android.app.AlertDialog;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MainActivityTest {
    private Spinner spinner(View v){if(v instanceof Spinner)return (Spinner)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Spinner s=spinner(g.getChildAt(i));if(s!=null)return s;}}return null;}
    @Test public void promotionSurvivesRecreationAndResetsAfterCheckout(){
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("zeropay.db");
        context.getSharedPreferences("MainActivity",0).edit().clear().commit();
        try(StoreDb db=new StoreDb(context)){
            db.save(new Product("PROMO","促销茶","饮料","瓶",1000,500,5,1),true);
            try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=controller.get();View decor=a.getWindow().getDecorView();
                firstInput(decor).setText("PROMO");find(decor,"添加条码").performClick();
                find(a.getWindow().getDecorView(),"促销：无促销").performClick();
                AlertDialog rule=ShadowAlertDialog.getLatestAlertDialog();spinner(rule.getWindow().getDecorView()).setSelection(1);
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                rule.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                controller.recreate();a=controller.get();
                assertNotNull(find(a.getWindow().getDecorView(),"促销：8.5 折"));
                assertNotNull(find(a.getWindow().getDecorView(),"¥ 8.50"));
                find(a.getWindow().getDecorView(),"确认收款  →").performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertEquals(850,db.scalar("SELECT total FROM sales"));assertEquals(150,db.scalar("SELECT discount FROM sales"));
                assertEquals(4,db.find("PROMO").stock);
                ShadowAlertDialog.getLatestAlertDialog().dismiss();
                decor=a.getWindow().getDecorView();firstInput(decor).setText("PROMO");find(decor,"添加条码").performClick();
                assertNotNull(find(a.getWindow().getDecorView(),"促销：无促销"));
                a.getPreferences(0).edit().clear().commit();
            }
        }
    }
    private TextView find(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().equals(s))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView result=find(g.getChildAt(i),s);if(result!=null)return result;}}return null;}
    @Test public void allMainScreensRender(){try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=controller.get();for(String tab:new String[]{"库存","流水","数据","收银"}){View decor=a.getWindow().getDecorView();TextView button=find(decor,tab);assertNotNull(button);button.performClick();assertNotNull(find(a.getWindow().getDecorView(),tab));}}}
    private EditText firstInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText e=firstInput(g.getChildAt(i));if(e!=null)return e;}}return null;}
    @Test public void scanGunEntryToCashCheckout(){
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("zeropay.db");
        try(StoreDb db=new StoreDb(context)){
            db.save(new Product("00123","测试茶","饮料","瓶",350,200,8,2),true);
            try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=controller.get();View decor=a.getWindow().getDecorView();firstInput(decor).setText("00123");find(decor,"添加条码").performClick();
                assertNotNull(find(a.getWindow().getDecorView(),"测试茶"));find(a.getWindow().getDecorView(),"确认收款  →").performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();firstInput(dialog.getWindow().getDecorView()).setText("5.00");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertEquals(7,db.find("00123").stock);assertEquals(350,db.scalar("SELECT total FROM sales"));assertEquals(500,db.scalar("SELECT paid FROM sales"));
                assertNotNull(find(a.getWindow().getDecorView(),"购物车还是空的"));
            }
        }
    }
}
