package com.zeropay.store;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MainActivityTest {
    @Test public void inventoryAddsAndDeletesWithConfirmation(){
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("zeropay.db");context.getSharedPreferences("MainActivity",0).edit().clear().commit();
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup();StoreDb db=new StoreDb(context)){
            MainActivity a=controller.get();navigate(a,"库存");find(a.getWindow().getDecorView(),"+ 添加商品").performClick();
            AlertDialog editor=latestDialog();java.util.List<EditText> fields=new java.util.ArrayList<>();inputs(editor.getWindow().getDecorView(),fields);
            fields.get(0).setText("000123");fields.get(1).setText("新增茶");fields.get(4).setText("2.50");fields.get(6).setText("8");
            editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals(8,db.find("000123").stock);
            find(a.getWindow().getDecorView(),"删除商品").performClick();latestDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNotNull(db.find("000123"));
            find(a.getWindow().getDecorView(),"删除商品").performClick();latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNull(db.find("000123"));
        }
    }
    private void navigate(MainActivity a,String tab){
        View label=find(a.getWindow().getDecorView(),tab);
        while(label!=null&&!label.isClickable())label=label.getParent() instanceof View?(View)label.getParent():null;
        assertNotNull(label);label.performClick();
    }
    private AlertDialog latestDialog(){return (AlertDialog)ShadowDialog.getLatestDialog();}
    private void inputs(View v,java.util.List<EditText> result){if(v instanceof EditText)result.add((EditText)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)inputs(g.getChildAt(i),result);}}
    private ListView list(View v){if(v instanceof ListView)return (ListView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){ListView found=list(g.getChildAt(i));if(found!=null)return found;}}return null;}
    @Test public void createBundleSelectRestoreAndCheckout(){
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("zeropay.db");context.getSharedPreferences("MainActivity",0).edit().clear().commit();
        try(StoreDb db=new StoreDb(context)){
            db.save(new Product("BUNDLE_A","套餐商品","","件",1000,500,10,0),true);
            try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=controller.get();navigate(a,"库存");
                find(a.getWindow().getDecorView(),"套餐管理").performClick();
                find(latestDialog().getWindow().getDecorView(),"+ 新建套餐").performClick();
                AlertDialog editor=latestDialog();
                java.util.List<EditText> fields=new java.util.ArrayList<>();inputs(editor.getWindow().getDecorView(),fields);
                fields.get(0).setText("双件套餐");fields.get(1).setText("15.00");
                find(editor.getWindow().getDecorView(),"添加组成商品").performClick();
                ListView products=list(latestDialog().getWindow().getDecorView());products.performItemClick(null,0,0);
                fields.clear();inputs(editor.getWindow().getDecorView(),fields);fields.get(2).setText("2");
                editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertEquals(1,db.bundles().size());assertEquals(2,db.bundles().get(0).items.get("BUNDLE_A").intValue());
                navigate(a,"收银");find(a.getWindow().getDecorView(),"选择套餐").performClick();
                find(latestDialog().getWindow().getDecorView(),"加入套餐").performClick();
                controller.recreate();a=controller.get();assertNotNull(find(a.getWindow().getDecorView(),"双件套餐"));assertNotNull(find(a.getWindow().getDecorView(),"¥ 15.00"));
                find(a.getWindow().getDecorView(),"确认收款  →").performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertEquals(1500,db.scalar("SELECT total FROM sales"));assertEquals(8,db.find("BUNDLE_A").stock);
                assertNotNull(find(a.getWindow().getDecorView(),"购物车还是空的"));
                a.getPreferences(0).edit().clear().commit();
            }
        }
    }
    private EditText visibleInput(View v){if(v.getVisibility()!=View.VISIBLE)return null;if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText e=visibleInput(g.getChildAt(i));if(e!=null)return e;}}return null;}
    private Spinner spinner(View v){if(v instanceof Spinner)return (Spinner)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Spinner s=spinner(g.getChildAt(i));if(s!=null)return s;}}return null;}
    @Test public void promotionSurvivesRecreationAndResetsAfterCheckout(){
        checkPromotionCheckout(1,"8.5 折",850);
    }
    @Test public void customPriceSurvivesRecreationAndResetsAfterCheckout(){
        checkPromotionCheckout(3,"自定义价格 ¥6.25",625);
    }
    private void checkPromotionCheckout(int type,String description,long total){
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("zeropay.db");
        context.getSharedPreferences("MainActivity",0).edit().clear().commit();
        try(StoreDb db=new StoreDb(context)){
            db.save(new Product("PROMO","促销茶","饮料","瓶",1000,500,5,1),true);
            try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=controller.get();View decor=a.getWindow().getDecorView();
                firstInput(decor).setText("PROMO");find(decor,"添加条码").performClick();
                find(a.getWindow().getDecorView(),"促销：无促销").performClick();
                AlertDialog rule=latestDialog();spinner(rule.getWindow().getDecorView()).setSelection(type);
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                if(type==3)visibleInput(rule.getWindow().getDecorView()).setText("6.25");
                rule.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                controller.recreate();a=controller.get();
                assertNotNull(find(a.getWindow().getDecorView(),"促销："+description));
                assertNotNull(find(a.getWindow().getDecorView(),"¥ "+Product.money(total)));
                find(a.getWindow().getDecorView(),"确认收款  →").performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertEquals(total,db.scalar("SELECT total FROM sales"));assertEquals(1000-total,db.scalar("SELECT discount FROM sales"));
                assertEquals(4,db.find("PROMO").stock);
                latestDialog().dismiss();
                decor=a.getWindow().getDecorView();firstInput(decor).setText("PROMO");find(decor,"添加条码").performClick();
                assertNotNull(find(a.getWindow().getDecorView(),"促销：无促销"));
                a.getPreferences(0).edit().clear().commit();
            }
        }
    }
    private TextView find(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().equals(s))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView result=find(g.getChildAt(i),s);if(result!=null)return result;}}return null;}
    @Test public void allMainScreensRender(){try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=controller.get();for(String tab:new String[]{"库存","流水","数据","收银"}){View decor=a.getWindow().getDecorView();TextView button=find(decor,tab);assertNotNull(button);navigate(a,tab);assertNotNull(find(a.getWindow().getDecorView(),tab));}}}
    private EditText firstInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText e=firstInput(g.getChildAt(i));if(e!=null)return e;}}return null;}
    @Test public void scanGunEntryToCashCheckout(){
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("zeropay.db");
        try(StoreDb db=new StoreDb(context)){
            db.save(new Product("00123","测试茶","饮料","瓶",350,200,8,2),true);
            try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=controller.get();View decor=a.getWindow().getDecorView();firstInput(decor).setText("00123");find(decor,"添加条码").performClick();
                assertNotNull(find(a.getWindow().getDecorView(),"测试茶"));find(a.getWindow().getDecorView(),"确认收款  →").performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                AlertDialog dialog=latestDialog();firstInput(dialog.getWindow().getDecorView()).setText("5.00");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertEquals(7,db.find("00123").stock);assertEquals(350,db.scalar("SELECT total FROM sales"));assertEquals(500,db.scalar("SELECT paid FROM sales"));
                assertNotNull(find(a.getWindow().getDecorView(),"购物车还是空的"));
            }
        }
    }
}
