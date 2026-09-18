package com.zeropay.store;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.appbar.MaterialToolbar;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.io.*;
import java.nio.charset.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    private int INK, GREEN, MUTED, BG, LINE, AMBER;
    private StoreDb db;
    private LinearLayout root,body,items,summary;
    private final LinkedHashMap<String,Integer> cart=new LinkedHashMap<>();
    private Promotion promotion=Promotion.NONE;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String page="收银", search="", exportKind="products", scanMode="cart";
    private boolean lowOnly=false, busy=false;
    private EditText barcodeTarget;
    private final ActivityResultLauncher<ScanOptions> scanner=registerForActivityResult(new ScanContract(),result->{
        if(result.getContents()==null) return;
        String code=result.getContents().trim();
        if(scanMode.equals("edit")&&barcodeTarget!=null) barcodeTarget.setText(code);
        else if(scanMode.equals("inventory")) { search=code; render(); }
        else addBarcode(code);
    });
    private final ActivityResultLauncher<String[]> importer=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
        if(uri==null)return;
        work(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                if(in==null)throw new IOException("无法打开文件");
                return Csv.products(new InputStreamReader(in,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)));
            }
        },this::previewImport);
    });
    private final ActivityResultLauncher<String> exporter=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),uri->{
        if(uri==null)return;
        final String kind=exportKind;
        work(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                if(out==null)throw new IOException("无法写入文件");
                try(Writer writer=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8))) { db.export(writer,kind); }
            }return "CSV 已导出";
        },this::toast);
    });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        INK=getColor(R.color.on_surface); GREEN=getColor(R.color.primary);
        MUTED=getColor(R.color.on_surface_variant); BG=getColor(R.color.surface);
        LINE=getColor(R.color.outline_variant); AMBER=getColor(R.color.warning);
        db=new StoreDb(this);
        if(state!=null) {
            page=state.getString("page","收银"); search=state.getString("search","");
            exportKind=state.getString("exportKind","products"); scanMode=state.getString("scanMode","cart");
            lowOnly=state.getBoolean("lowOnly",false);
        }
        restoreCart(); render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("page",page); state.putString("search",search);
        state.putString("exportKind",exportKind); state.putString("scanMode",scanMode); state.putBoolean("lowOnly",lowOnly);
    }
    @Override protected void onDestroy() { io.execute(db::close); io.shutdown(); super.onDestroy(); }
    private void restoreCart() {
        android.content.SharedPreferences prefs=getPreferences(0);
        try { promotion=new Promotion(prefs.getInt("promotionType",0),prefs.getLong("promotionThreshold",0),prefs.getLong("promotionValue",0)); }
        catch(IllegalArgumentException e) { promotion=Promotion.NONE; }
        String text=getPreferences(0).getString("cart","");
        for(String line:text.split("\n")) {
            String[] p=line.split(":");
            if(p.length==2)try{cart.put(p[0],Integer.parseInt(p[1]));}catch(NumberFormatException ignored){}
        }
    }
    private void persistCart() {
        if(cart.isEmpty())promotion=Promotion.NONE;
        StringBuilder s=new StringBuilder(); for(Map.Entry<String,Integer> e:cart.entrySet())s.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        getPreferences(0).edit().putString("cart",s.toString()).putInt("promotionType",promotion.type)
            .putLong("promotionThreshold",promotion.threshold).putLong("promotionValue",promotion.value).apply();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(24));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView label(String text,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(5),0,dp(5));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private TextView mono(String text,int size,int color){TextView v=label(text,size,color,true);v.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);return v;}
    private Button button(String text,boolean primary,Runnable action){
        int style=primary?com.google.android.material.R.attr.materialButtonStyle:com.google.android.material.R.attr.materialButtonTonalStyle;
        MaterialButton b=new MaterialButton(this,null,style);
        b.setText(text);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(48));
        b.setMinimumWidth(0);b.setMinWidth(0);b.setPadding(dp(16),dp(4),dp(16),dp(4));
        b.setOnClickListener(v->{if(!busy)action.run();});return b;
    }
    private void put(LinearLayout p,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);p.addView(v,lp);}
    private void weighted(LinearLayout p,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);p.addView(v,lp);}
    private LinearLayout card(LinearLayout parent){
        MaterialCardView surface=new MaterialCardView(this);
        surface.setRadius(dp(20));surface.setCardElevation(0);surface.setStrokeWidth(0);
        surface.setCardBackgroundColor(getColor(R.color.surface_low));
        LinearLayout c=column();c.setPadding(dp(16),dp(16),dp(16),dp(12));
        surface.addView(c);put(parent,surface);return c;
    }
    private EditText input(LinearLayout parent,String title,String value,int type){
        TextInputLayout field=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);
        field.setHint(title);field.setBoxCornerRadii(dp(12),dp(12),dp(12),dp(12));
        TextInputEditText e=new TextInputEditText(field.getContext());
        e.setTextSize(16);e.setSingleLine(true);e.setInputType(type);e.setText(value);
        e.setMinimumHeight(dp(56));field.addView(e,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(6);lp.bottomMargin=dp(14);parent.addView(field,lp);return e;
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void error(Exception e){new MaterialAlertDialogBuilder(this).setTitle("未完成操作").setMessage(e.getMessage()==null?"操作失败，请重试":e.getMessage()).setPositiveButton("知道了",null).show();}
    private String value(EditText e){return e.getText().toString().trim();}
    private String time(String ms){return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(Long.parseLong(ms)));}
    private <T> void work(Callable<T> task,java.util.function.Consumer<T> done){
        if(busy)return;busy=true;toast("正在处理，请稍候…");
        io.execute(()->{try{T result=task.call();runOnUiThread(()->{busy=false;if(!isDestroyed()){render();done.accept(result);}});}catch(Exception e){runOnUiThread(()->{busy=false;if(!isDestroyed())error(e);});}});
    }
    private void render(){
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),0,dp(18),0);
        if(android.os.Build.VERSION.SDK_INT>=30) root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
            v.setPadding(dp(18)+bars.left,bars.top,dp(18)+bars.right,bars.bottom);return insets;
        });
        if(android.os.Build.VERSION.SDK_INT<30) root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(18)+i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),dp(18)+i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        setContentView(root);root.requestApplyInsets();
        MaterialToolbar brand=new MaterialToolbar(this);
        brand.setTitle("ZeroPay");brand.setSubtitle("本机 · 离线");
        brand.setTitleTextColor(INK);brand.setSubtitleTextColor(MUTED);
        root.addView(brand,new LinearLayout.LayoutParams(-1,dp(64)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=column();body.setPadding(0,dp(12),0,dp(10));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        put(body,label(page,30,INK,true));
        switch(page){case "收银":cashier();break;case "库存":inventory();break;case "流水":history();break;default:data();}
        BottomNavigationView nav=new BottomNavigationView(this);
        nav.setBackgroundColor(getColor(R.color.surface_container));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(nav,(v,insets)->insets);
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setItemHorizontalTranslationEnabled(false);
        String[] tabs={"收银","库存","流水","数据"};
        int[] icons={R.drawable.ic_cashier,R.drawable.ic_inventory,R.drawable.ic_history,R.drawable.ic_data};
        for(int i=0;i<tabs.length;i++){
            nav.getMenu().add(0,i+1,i,tabs[i]).setIcon(icons[i]);
            if(tabs[i].equals(page))nav.getMenu().getItem(i).setChecked(true);
        }
        nav.setOnItemSelectedListener(item->{
            if(busy)return false;
            String next=item.getTitle().toString();
            if(!page.equals(next)){page=next;search="";render();}
            return true;
        });
        root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
    }
    private void scan(String mode){scanMode=mode;scanner.launch(new ScanOptions().setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES).setPrompt("将条码放入取景框 · 支持商品条码与二维码").setBeepEnabled(true).setCaptureActivity(PortraitCaptureActivity.class).setOrientationLocked(true));}
    private void cashier(){
        put(body,label("扫商品条码，开始一笔新交易",14,MUTED,false));
        LinearLayout panel=card(body);
        put(panel,button("▥   扫码添加商品",true,()->scan("cart")));
        EditText code=input(panel,"输入条码 / 扫码枪输入", "", android.text.InputType.TYPE_CLASS_TEXT);
        ((TextInputLayout)code.getParent().getParent()).setPlaceholderText("如 6901234567892");code.setImeOptions(EditorInfo.IME_ACTION_DONE);
        code.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_DONE||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN)){if(!busy){addBarcode(value(code));code.setText("");}return true;}return false;});
        LinearLayout actions=row();weighted(actions,button("添加条码",false,()->{addBarcode(value(code));code.setText("");}));weighted(actions,button("选择商品",false,this::chooseProduct));put(panel,actions);
        put(panel,button("选择套餐",false,()->bundleList(false)));
        LinearLayout title=row();weighted(title,label("购物清单",18,INK,true));title.addView(button("清空",false,()->{if(!cart.isEmpty())new MaterialAlertDialogBuilder(this).setTitle("清空购物车？").setMessage("商品将从本次交易中移除。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{cart.clear();persistCart();refreshCart();}).show();}));put(body,title);
        items=column();put(body,items);summary=column();root.addView(summary);refreshCart();
    }
    private void refreshCart(){
        if(items==null||summary==null||!page.equals("收银"))return;items.removeAllViews();summary.removeAllViews();
        long total=0;int count=0;
        if(cart.isEmpty()){LinearLayout empty=card(items);put(empty,label("购物车还是空的",20,INK,true));put(empty,label("扫描商品，或从商品库中选择。\n未建档的条码可直接新建商品。",14,MUTED,false));}
        for(Map.Entry<String,Integer> e:cart.entrySet()){
            Product p=db.cartProduct(e.getKey());if(p==null){String key=e.getKey();put(items,button("商品或套餐已删除，点击移除",false,()->{cart.remove(key);persistCart();refreshCart();}));continue;}int qty=e.getValue();total+=p.price*qty;count+=qty;
            LinearLayout c=card(items);put(c,label(p.name,18,INK,true));put(c,mono(p.barcode,12,MUTED));
            if(p.barcode.startsWith("@"))put(c,label(db.bundleContents(db.bundle(p.barcode)),13,MUTED,false));
            LinearLayout r=row();LinearLayout left=column();put(left,label("¥"+Product.money(p.price)+" / "+p.unit+" · 库存 "+p.stock,13,qty>p.stock?AMBER:MUTED,false));put(left,mono("¥"+Product.money(p.price*qty),22,INK));weighted(r,left);
            Button minus=button("−",false,()->{if(qty==1)cart.remove(p.barcode);else cart.put(p.barcode,qty-1);persistCart();refreshCart();});minus.setContentDescription("减少 "+p.name+" 数量");r.addView(minus,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView n=mono("  "+qty+"  ",17,INK);n.setMinimumWidth(dp(48));n.setMinimumHeight(dp(48));n.setGravity(Gravity.CENTER);n.setContentDescription("数量 "+qty+"，点击修改");n.setOnClickListener(v->{if(!busy)cartQuantity(p,qty);});r.addView(n);
            Button plus=button("+",false,()->addBarcode(p.barcode));plus.setContentDescription("增加 "+p.name+" 数量");r.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(48)));put(c,r);
        }
        if(!cart.isEmpty()) {
            LinearLayout offer=card(items);
            put(offer,button("促销："+promotion.description(),false,this::promotionDialog));
            long discounted=promotion.total(total);
            put(offer,label("商品/套餐合计 ¥"+Product.money(total)+" · 优惠 ¥"+Product.money(total-discounted),14,GREEN,true));
            if(promotion.type==2 && total<promotion.threshold)put(offer,label("还差 ¥"+Product.money(promotion.threshold-total)+" 可享满减",13,AMBER,false));
            total=discounted;
        }
        LinearLayout receipt=row();receipt.setPadding(dp(14),dp(8),dp(14),dp(8));receipt.setBackground(shape(getColor(R.color.primary_container),0));
        LinearLayout amount=column();amount.addView(label("应收合计 · "+count+" 件/套",12,getColor(R.color.on_primary_container),true));
        TextView sum=mono("¥ "+Product.money(total),27,INK);sum.setSingleLine(true);sum.setAutoSizeTextTypeUniformWithConfiguration(12,27,1,android.util.TypedValue.COMPLEX_UNIT_SP);amount.addView(sum,new LinearLayout.LayoutParams(-1,dp(42)));weighted(receipt,amount);
        Button checkout=button("确认收款  →",true,this::payment);checkout.setEnabled(!cart.isEmpty());weighted(receipt,checkout);summary.addView(receipt,new LinearLayout.LayoutParams(-1,-2));
    }
    private void cartQuantity(Product p,int old){
        LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),0);EditText n=input(f,"数量（0 表示移除）",String.valueOf(old),2);
        form("修改数量",f,()->{int q=Product.quantity(value(n));Map<String,Integer> next=new LinkedHashMap<>(cart);if(q==0)next.remove(p.barcode);else next.put(p.barcode,q);db.total(next);cart.clear();cart.putAll(next);persistCart();refreshCart();});
    }
    private void addBarcode(String code){
        if(code.isEmpty()){toast("请先输入商品条码");return;}
        Product p=db.cartProduct(code);
        if(p==null){new MaterialAlertDialogBuilder(this).setTitle("商品尚未建档").setMessage("条码："+code).setNegativeButton("取消",null).setPositiveButton("新建商品",(d,w)->editProduct(null,code)).show();return;}
        int next=cart.getOrDefault(code,0)+1;
        Map<String,Integer> candidate=new LinkedHashMap<>(cart);candidate.put(code,next);
        try{db.total(candidate);}catch(Exception e){error(e);return;}
        cart.put(code,next);persistCart();refreshCart();
    }
    private void chooseProduct(){
        chooseProduct(p->addBarcode(p.barcode));
    }
    private void chooseProduct(java.util.function.Consumer<Product> chosen){
        LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),0);EditText q=input(f,"商品名 / 条码 / 分类","",1);
        ListView list=new ListView(this);f.addView(list,new LinearLayout.LayoutParams(-1,dp(320)));
        final List<Product> found=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());list.setAdapter(adapter);
        Runnable update=()->{found.clear();found.addAll(db.products(value(q),false));adapter.clear();for(Product p:found)adapter.add(p.name+"  ¥"+Product.money(p.price)+"\n"+p.barcode+" · 库存 "+p.stock);};update.run();watch(q,update);
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("选择商品").setView(f).setNegativeButton("返回",null).create();
        list.setOnItemClickListener((parent,v,pos,id)->{chosen.accept(found.get(pos));dialog.dismiss();});dialog.show();
    }
    private void bundleList(boolean manage){
        LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),0);
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(manage?"套餐管理":"选择套餐").setNegativeButton("关闭",null).create();
        if(manage)put(f,button("+ 新建套餐",true,()->{dialog.dismiss();editBundle(null);}));
        List<BundleOffer> bundles=db.bundles();
        if(bundles.isEmpty())put(f,label("暂无套餐，请到库存 → 套餐管理创建。",15,MUTED,false));
        for(BundleOffer b:bundles){
            LinearLayout c=card(f);put(c,label(b.name+" · ¥"+Product.money(b.price)+" / 套",18,INK,true));
            put(c,label(db.bundleContents(b)+"\n最多可售 "+db.cartProduct(b.id).stock+" 套（以结账时合计库存为准）",13,MUTED,false));
            if(manage){
                put(c,button("编辑套餐",false,()->{dialog.dismiss();editBundle(b);}));
                put(c,button("删除套餐",false,()->new MaterialAlertDialogBuilder(this).setTitle("删除套餐？").setMessage("删除「"+b.name+"」，并从当前购物车移除；历史订单保留。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{db.deleteBundle(b.id);cart.remove(b.id);persistCart();dialog.dismiss();render();}).show()));
            }else put(c,button("加入套餐",true,()->{addBarcode(b.id);dialog.dismiss();}));
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(f);dialog.setView(scroll);dialog.show();
    }
    private void editBundle(BundleOffer old){
        LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),0);
        EditText name=input(f,"套餐名称",old==null?"":old.name,1);
        EditText price=input(f,"组合售价（元 / 套）",old==null?"":Product.money(old.price),8194);
        put(f,label("选择组成商品并填写每套数量；数量设为 0 可移除。套餐无独立库存，按组成商品扣库。整单促销在套餐价基础上计算。",13,MUTED,false));
        LinearLayout parts=column();put(f,parts);
        Map<String,EditText> quantities=new LinkedHashMap<>();
        java.util.function.BiConsumer<Product,Integer> add=(p,qty)->{
            if(quantities.containsKey(p.barcode)){toast("该商品已添加，请修改数量");return;}
            quantities.put(p.barcode,input(parts,p.name+" ["+p.barcode+"] 每套数量",String.valueOf(qty),2));
        };
        if(old!=null)for(Map.Entry<String,Integer> e:old.items.entrySet())add.accept(db.find(e.getKey()),e.getValue());
        put(f,button("添加组成商品",false,()->chooseProduct(p->add.accept(p,1))));
        form(old==null?"新建套餐":"编辑套餐",f,()->{
            Map<String,Integer> selected=new LinkedHashMap<>();
            for(Map.Entry<String,EditText> e:quantities.entrySet()){int q=Product.quantity(value(e.getValue()));if(q>0)selected.put(e.getKey(),q);}
            db.saveBundle(new BundleOffer(old==null?"@"+UUID.randomUUID():old.id,value(name),Product.cents(value(price)),selected));
            render();toast("套餐已保存");
        });
    }
    private void promotionDialog(){
        LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),0);
        Spinner type=new androidx.appcompat.widget.AppCompatSpinner(this);type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"无促销","整单打折","满减","自定义价格"}));put(f,type);
        LinearLayout discountFields=column(),reductionFields=column(),customFields=column();put(f,discountFields);put(f,reductionFields);put(f,customFields);
        EditText rate=input(discountFields,"折扣（0.1–9.9 折，如 8.5）",promotion.type==1?java.math.BigDecimal.valueOf(promotion.value,1).toPlainString():"8.5",8194);
        EditText threshold=input(reductionFields,"满多少元",promotion.type==2?Product.money(promotion.threshold):"100.00",8194);
        EditText reduction=input(reductionFields,"减多少元",promotion.type==2?Product.money(promotion.value):"10.00",8194);
        EditText customPrice=input(customFields,"本单应收金额（元）",Product.money(promotion.type==3?promotion.value:promotion.total(db.total(cart))),8194);
        Runnable visibility=()->{discountFields.setVisibility(type.getSelectedItemPosition()==1?View.VISIBLE:View.GONE);reductionFields.setVisibility(type.getSelectedItemPosition()==2?View.VISIBLE:View.GONE);customFields.setVisibility(type.getSelectedItemPosition()==3?View.VISIBLE:View.GONE);};
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){visibility.run();}});
        type.setSelection(promotion.type);visibility.run();
        put(f,label("仅用于本单，不叠加。打折应收四舍五入到分；满减达到门槛减一次。自定义价格支持 0–1000000 元、最多两位小数，应收不超过商品原价合计。清空或结账后自动取消促销。",13,MUTED,false));
        form("设置促销",f,()->{
            int selected=type.getSelectedItemPosition();
            promotion=selected==0?Promotion.NONE:selected==1?Promotion.discount(value(rate)):selected==2?new Promotion(2,Product.cents(value(threshold)),Product.cents(value(reduction))):new Promotion(3,0,Product.cents(value(customPrice)));
            persistCart();refreshCart();
        });
    }
    private void payment(){
        final Promotion applied=promotion;
        final String bundles;try{bundles=db.bundleSnapshot(cart);}catch(Exception e){error(e);return;}
        final long subtotal,total;try{subtotal=db.total(cart);total=applied.total(subtotal);}catch(Exception e){error(e);return;}
        LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),0);put(f,mono("应收 ¥"+Product.money(total),28,INK));
        if(!bundles.isEmpty())put(f,label(bundles.substring(1),14,MUTED,false));
        put(f,label("商品/套餐合计 ¥"+Product.money(subtotal)+"\n"+applied.description()+" · 优惠 ¥"+Product.money(subtotal-total),14,GREEN,false));
        put(f,label("收款方式",14,MUTED,false));Spinner method=new androidx.appcompat.widget.AppCompatSpinner(this);String[] methods={"现金","微信（已收款）","支付宝（已收款）","其他（已收款）"};method.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,methods));put(f,method);
        EditText paid=input(f,"实收金额（元）",Product.money(total),8194);TextView change=label("找零 ¥0.00",16,GREEN,true);put(f,change);
        watch(paid,()->{try{long n=Product.cents(value(paid));change.setText(n>=total?"找零 ¥"+Product.money(n-total):"实收金额不足");}catch(Exception e){change.setText("请输入有效金额");}});
        method.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){paid.setEnabled(pos==0);paid.setText(Product.money(total));}});
        put(f,label("微信、支付宝等仅记录已收到的款项，请先在对应收款工具中核实到账。",13,MUTED,false));
        form("结账",f,()->{
            long amount=Product.cents(value(paid));String id=db.checkout(new LinkedHashMap<>(cart),subtotal,amount,methods[method.getSelectedItemPosition()],applied,bundles);cart.clear();persistCart();render();
            new MaterialAlertDialogBuilder(this).setTitle("收款已记录").setMessage("订单 "+id.substring(0,8)+"\n合计 ¥"+Product.money(total)+"\n找零 ¥"+Product.money(amount-total)+"\n库存已同步扣减").setPositiveButton("完成",null).show();
        });
    }
    private void inventory(){
        long all=db.scalar("SELECT count(*) FROM products WHERE deleted=0"),low=db.scalar("SELECT count(*) FROM products WHERE deleted=0 AND stock<=min_stock");
        LinearLayout metrics=row();weighted(metrics,metric("商品种类",String.valueOf(all),INK));weighted(metrics,metric("库存预警",String.valueOf(low),AMBER));put(body,metrics);
        LinearLayout actions=row();weighted(actions,button("+ 添加商品",true,()->editProduct(null,"")));weighted(actions,button("扫码查库存",false,()->scan("inventory")));put(body,actions);
        put(body,button("套餐管理",false,()->bundleList(true)));
        EditText q=input(body,"搜索商品",search,1);((TextInputLayout)q.getParent().getParent()).setPlaceholderText("名称、条码或分类");
        CheckBox check=new MaterialCheckBox(this);check.setText("仅显示库存预警商品");check.setTextColor(INK);check.setChecked(lowOnly);put(body,check);
        LinearLayout results=column();put(body,results);
        Runnable update=()->{search=value(q);results.removeAllViews();List<Product> products=db.products(search,lowOnly);
            if(products.isEmpty()){put(results,label(all==0?"先创建商品，或到「数据」导入 CSV。":"没有找到匹配的商品",16,MUTED,false));return;}
            put(results,label("找到 "+products.size()+" 种商品"+(products.size()>200?"，显示前 200 种，请缩小搜索范围":""),12,MUTED,false));
            for(Product p:products.subList(0,Math.min(products.size(),200))){
                LinearLayout c=card(results);LinearLayout r=row();LinearLayout info=column();put(info,label(p.name,18,INK,true));put(info,mono(p.barcode,12,MUTED));weighted(r,info);r.addView(mono(String.valueOf(p.stock)+" "+p.unit,20,p.stock<=p.minimum?AMBER:GREEN));put(c,r);
                put(c,label((p.category.isEmpty()?"未分类":p.category)+"  ·  售价 ¥"+Product.money(p.price)+"  ·  预警 ≤ "+p.minimum,13,MUTED,false));
                LinearLayout tools=row();weighted(tools,button("入 / 出库",true,()->stockDialog(p)));weighted(tools,button("编辑",false,()->editProduct(p,p.barcode)));put(c,tools);
                put(c,button("删除商品",false,()->deleteProduct(p)));
            }
        };watch(q,update);check.setOnCheckedChangeListener((b,checked)->{lowOnly=checked;update.run();});update.run();
    }
    private void deleteProduct(Product p){
        new MaterialAlertDialogBuilder(this).setTitle("删除商品？")
            .setMessage("删除「"+p.name+"」（"+p.barcode+"）后，将从库存和收银中隐藏，并从购物车移除。历史订单、库存流水和退货保留。重新添加同一条码可恢复商品，保留原库存（含退货），不使用填写的初始库存。")
            .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
                try{db.deleteProduct(p.barcode);cart.remove(p.barcode);persistCart();render();toast("商品已删除");}catch(Exception e){error(e);}
            }).show();
    }
    private LinearLayout metric(String title,String value,int color){LinearLayout c=column();c.setPadding(dp(16),dp(12),dp(12),dp(12));c.setBackground(shape(getColor(R.color.surface_container),0));put(c,label(title,13,MUTED,false));TextView n=mono(value,28,color);n.setSingleLine(true);n.setAutoSizeTextTypeUniformWithConfiguration(12,28,1,android.util.TypedValue.COMPLEX_UNIT_SP);c.addView(n,new LinearLayout.LayoutParams(-1,dp(48)));return c;}
    private void editProduct(Product old,String barcode){
        LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),0);
        EditText code=input(f,"商品条码",barcode,1);code.setEnabled(old==null);barcodeTarget=code;
        if(old==null)put(f,button("扫描条码",false,()->scan("edit")));
        EditText name=input(f,"商品名称",old==null?"":old.name,1),category=input(f,"分类",old==null?"":old.category,1),unit=input(f,"单位",old==null?"件":old.unit,1);
        EditText price=input(f,"零售价（元）",old==null?"":Product.money(old.price),8194),cost=input(f,"成本价（元）",old==null?"0.00":Product.money(old.cost),8194);
        EditText stock=input(f,old==null?"初始库存":"当前库存（通过入 / 出库变更）",old==null?"0":String.valueOf(old.stock),2);stock.setEnabled(old==null);
        EditText min=input(f,"库存预警值",old==null?"5":String.valueOf(old.minimum),2);
        form(old==null?"新建商品":"编辑商品",f,()->{Product p=new Product(value(code),value(name),value(category),value(unit),Product.cents(value(price)),Product.cents(value(cost)),Product.quantity(value(stock)),Product.quantity(value(min)));db.save(p,old==null);render();toast("商品已保存");});
    }
    private void stockDialog(Product p){
        LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),0);put(f,label(p.name+" · 当前 "+p.stock+" "+p.unit,18,INK,true));
        Spinner type=new androidx.appcompat.widget.AppCompatSpinner(this);String[] types={"采购入库","领用 / 损耗出库","盘点：设置实际库存"};type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));put(f,type);
        EditText qty=input(f,"数量", "",2),reason=input(f,"备注 / 原因","",1);
        form("库存变更",f,()->{int n=Product.quantity(value(qty));int pos=type.getSelectedItemPosition();if(pos!=2&&n==0)throw new IllegalArgumentException("入 / 出库数量须大于 0");if(value(reason).isEmpty())throw new IllegalArgumentException("请填写备注 / 原因");db.adjust(p.barcode,pos==1?-n:n,pos==2,types[pos]+"："+value(reason));render();toast("库存已更新");});
    }
    private void history(){
        long today=java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        LinearLayout metrics=row();weighted(metrics,metric("今日有效销售额","¥"+Product.money(db.scalar("SELECT COALESCE(sum(total),0) FROM sales WHERE refunded=0 AND time>="+today)),GREEN));weighted(metrics,metric("今日有效订单",String.valueOf(db.scalar("SELECT count(*) FROM sales WHERE refunded=0 AND time>="+today)),INK));put(body,metrics);
        put(body,label("销售订单",20,INK,true));put(body,label("最近 100 笔 · 完整历史可导出 CSV",12,MUTED,false));
        List<String[]> sales=db.rows("SELECT id,time,total,method,refunded FROM sales ORDER BY time DESC LIMIT 100");
        if(sales.isEmpty())put(body,label("还没有销售订单，完成首笔收款后会显示在这里。",15,MUTED,false));
        for(String[] s:sales){LinearLayout c=card(body);LinearLayout r=row();weighted(r,mono("¥"+Product.money(Long.parseLong(s[2])),24,INK));r.addView(label(s[4].equals("1")?"已退货":"已完成",13,s[4].equals("1")?AMBER:GREEN,true));put(c,r);put(c,label(time(s[1])+" · "+s[3]+" · "+s[0].substring(0,8),12,MUTED,false));put(c,button("查看订单",false,()->saleDetail(s[0])));}
        put(body,label("库存流水",20,INK,true));put(body,label("最近 100 条 · 入库、出库、盘点与退货",12,MUTED,false));
        List<String[]> movements=db.rows("SELECT m.time,p.name,m.delta,m.balance,m.reason FROM movements m JOIN products p ON p.barcode=m.barcode ORDER BY m.id DESC LIMIT 100");
        if(movements.isEmpty())put(body,label("暂无库存变更记录",15,MUTED,false));
        for(String[] m:movements){LinearLayout c=card(body);put(c,label(m[1]+"    "+(Integer.parseInt(m[2])>0?"+":"")+m[2]+"  →  "+m[3],16,INK,true));put(c,label(time(m[0])+" · "+m[4],12,MUTED,false));}
    }
    private void saleDetail(String id){
        String[] sale=db.rows("SELECT time,total,paid,method,refunded,subtotal,discount,promotion FROM sales WHERE id=?",id).get(0);
        StringBuilder text=new StringBuilder("订单 "+id+"\n"+time(sale[0])+" · "+sale[3]+"\n\n");
        if(sale[7].contains("；套餐："))text.append("组成商品明细（以下金额按单品零售价展示）：\n");
        for(String[] r:db.rows("SELECT name,qty,price FROM sale_items WHERE sale_id=?",id))text.append(r[0]).append(" × ").append(r[1]).append("   ¥").append(Product.money(Long.parseLong(r[1])*Long.parseLong(r[2]))).append('\n');
        text.append("\n商品/套餐合计 ¥").append(Product.money(Long.parseLong(sale[5]))).append("\n促销及套餐：").append(sale[7]).append("\n整单优惠 ¥").append(Product.money(Long.parseLong(sale[6])));
        text.append("\n合计 ¥").append(Product.money(Long.parseLong(sale[1]))).append("\n实收 ¥").append(Product.money(Long.parseLong(sale[2]))).append("\n找零 ¥").append(Product.money(Long.parseLong(sale[2])-Long.parseLong(sale[1])));
        AlertDialog.Builder b=new MaterialAlertDialogBuilder(this).setTitle(sale[4].equals("1")?"订单已退货":"订单详情").setMessage(text).setPositiveButton("关闭",null);
        if(sale[4].equals("0"))b.setNeutralButton("整单退货",(d,w)->new MaterialAlertDialogBuilder(this).setTitle("确认整单退货？").setMessage("全部商品将恢复库存，订单标记为已退货。\n请在外部收款工具中自行完成退款 ¥"+Product.money(Long.parseLong(sale[1]))+"。此操作不会发起资金退款。").setNegativeButton("取消",null).setPositiveButton("退款已处理，记录退货",(dd,ww)->{try{db.refund(id);render();toast("退货已记录，库存已恢复");}catch(Exception e){error(e);}}).show());b.show();
    }
    private void data(){
        put(body,label("CSV 数据交换",16,MUTED,false));
        LinearLayout c=card(body);put(c,label("导入商品",22,INK,true));put(c,label("按条码匹配，更新名称、价格和分类。新条码自动建档。导入前会检查格式并显示预览。",14,MUTED,false));
        put(c,button("选择 CSV 文件",true,()->importer.launch(new String[]{"text/*","application/csv","application/vnd.ms-excel","application/octet-stream"})));
        put(c,button("导出商品模板",false,()->export("template")));
        LinearLayout out=card(body);put(out,label("导出数据",22,INK,true));put(out,label("UTF-8 编码，兼容带逗号、换行的商品名称。文件保存位置由你选择。",14,MUTED,false));
        put(out,button("导出商品与当前库存",false,()->export("products")));put(out,button("导出销售明细",false,()->export("sales")));put(out,button("导出库存流水",false,()->export("movements")));
        LinearLayout note=card(body);put(note,label("使用说明",18,INK,true));put(note,label("• 条码作为文本保存，支持前导零。用表格软件打开时，请把条码列设为文本。\n\n• 售价与成本单位为元，最多两位小数；库存为非负整数。\n\n• 商品 CSV 支持重新导入；销售与库存流水 CSV 用于查账，不支持导入恢复历史订单。\n\n• 数据保存在本机，卸载应用会删除数据，请定期导出。\n\n• 相机扫码需要相机权限；也可手输条码或使用回车结尾的扫码枪。",14,MUTED,false));
        put(body,label("ZeroPay 零点收银  1.2.0 · 单店离线版",12,MUTED,false));
    }
    private void export(String kind){exportKind=kind;exporter.launch("ZeroPay_"+kind+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+".csv");}
    private void previewImport(List<Product> products){
        int existing=0;for(Product p:products)if(db.find(p.barcode)!=null)existing++;
        LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),0);put(f,label("共 "+products.size()+" 条 · 新增 "+(products.size()-existing)+" · 更新 "+existing,18,INK,true));
        for(Product p:products.subList(0,Math.min(5,products.size())))put(f,label(p.barcode+"  "+p.name+"\n¥"+Product.money(p.price)+" · CSV 库存 "+p.stock,13,MUTED,false));
        CheckBox overwrite=new MaterialCheckBox(this);overwrite.setText("用 CSV 覆盖已有商品库存");put(f,overwrite);put(f,label("默认保留已有库存，新商品使用 CSV 库存。勾选后按 CSV 数量盘点，并记录差异流水。未列出的商品保持原样。",13,MUTED,false));
        form("确认导入",f,()->{boolean replace=overwrite.isChecked();work(()->{db.importProducts(products,replace);return "已导入 "+products.size()+" 条商品";},this::toast);});
    }
    private void form(String title,LinearLayout content,Runnable save){
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(title).setView(scroll).setNegativeButton("取消",null).setPositiveButton("确认",null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(busy)return;try{save.run();dialog.dismiss();}catch(Exception e){error(e);}});
    }
    private void watch(EditText e,Runnable changed){e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){changed.run();}public void afterTextChanged(Editable v){}});}
}
