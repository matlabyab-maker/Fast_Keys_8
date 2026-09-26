package com.keyforge.alphaone;

import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.view.*;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.util.*;

public class KeyForgeKeyboardView extends View {
    private final KeyForgeInputMethodService service;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap referenceKeyboard;
    private final Handler handler = new Handler();
    private float gap, keyH;
    private boolean caps = false;
    private Runnable repeat;
    private float repeatX, repeatY;
    // User-controlled keyboard resize mode. Height is persisted locally.
    private boolean resizeMode = false;
    private boolean resizingKeyboard = false;
    private float resizeStartY = 0f;
    private int resizeStartHeight = 0;
    private final int defaultHeightDp = 320;
    private final int minHeightDp = 220;
    private final int maxHeightDp = 620;
    // Visual key-press light; this changes only the pressed-key appearance.
    private boolean pressGlow = false;
    private boolean pressHeld = false;
    private float pressL, pressT, pressR, pressB;
    private final Runnable clearPressGlow = () -> {
        if (!pressHeld) {
            pressGlow = false;
            invalidate();
        }
    };
    private final String[] suggestions = new String[6];
    private float suggestionH;
    private static final String[][] WORDS = {
        {"سلام","سلامت","سلامتی"},{"من","منم","منطقه"},{"این","اینجا","اینجانب"},
        {"برای","برنامه","بررسی"},{"کیبورد","کیبوردی","کیبوردها"},{"است","استفاده","استان"},
        {"یک","یکی","یکم"},{"دارم","دارد","دارند"},{"می","میرم","میز"},{"خوب","خوبه","خوبی"},
        {"تایپ","تایپی","تایپ کردن"},{"کلمه","کلمات","کلمه‌های"}
    };
    private final int BG=Color.rgb(239,238,232), DEFAULT_KEY=Color.rgb(250,249,244),
            BLUE=Color.rgb(20,112,235), NAVY=Color.rgb(18,38,78), BLACK=Color.rgb(25,29,34),
            GREEN=Color.rgb(45,205,55), ENTER_BG=Color.rgb(225,238,255), BACKSPACE_BG=Color.rgb(255,232,232), NUMBER_BG=Color.rgb(232,231,224), SPACE_BG=Color.rgb(242,224,145);
    private boolean englishMode = false;
    private boolean hideTopRow = false;
    private boolean hideSuggestionRow = false;
    private static final float[] SOURCE_BANDS = {0f,122f,206f,342f,470f,600f,722f,856f};
    private int KEY;

    public KeyForgeKeyboardView(KeyForgeInputMethodService s){
        super(s);
        service=s;
        referenceKeyboard = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier("keyboard_reference", "drawable", service.getPackageName()));
        KEY = service.getSharedPreferences("keyforge_alphaone_settings", android.content.Context.MODE_PRIVATE)
                .getInt("keyboard_key_color", DEFAULT_KEY);
        setBackgroundColor(BG);
        int savedAlpha = service.getSharedPreferences("keyforge_alphaone_settings", 0).getInt("keyboard_alpha", 100);
        setAlpha(Math.max(1, Math.min(100, savedAlpha)) / 100f);
        post(() -> applyKeyboardHeightDp(savedKeyboardHeightDp()));
    }

    private void txt(Canvas c,String s,float x,float y,float size,int color){
        p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(s,x,y-(p.ascent()+p.descent())/2,p);
    }

    private void key(Canvas c,float l,float t,float r,float b,String label,int color,boolean square){
        p.setColor(KEY);
        p.setStyle(Paint.Style.FILL);
        float rad=square?3:7;
        c.drawRoundRect(l,t,r,b,rad,rad,p);
        p.setColor(Color.rgb(205,204,199));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        c.drawRoundRect(l,t,r,b,rad,rad,p);
        p.setStyle(Paint.Style.FILL);
        if(label!=null&&!label.isEmpty())
            txt(c,label,(l+r)/2,(t+b)/2,Math.min(22,(b-t)*.42f),color);
    }

    private void keyWithBackground(Canvas c,float l,float t,float r,float b,String label,int textColor,int backgroundColor,boolean square){
        p.setColor(backgroundColor); p.setStyle(Paint.Style.FILL);
        float rad=square?3:7; c.drawRoundRect(l,t,r,b,rad,rad,p);
        p.setColor(Color.rgb(205,204,199)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1);
        c.drawRoundRect(l,t,r,b,rad,rad,p); p.setStyle(Paint.Style.FILL);
        if(label!=null&&!label.isEmpty()) txt(c,label,(l+r)/2,(t+b)/2,Math.min(22,(b-t)*.42f),textColor);
    }

    private void magnifierIcon(Canvas c,float cx,float cy,float size,boolean active){
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2,size*.10f));
        p.setColor(active?GREEN:NAVY);
        c.drawCircle(cx-size*.10f,cy-size*.10f,size*.25f,p);
        c.drawLine(cx+size*.08f,cy+size*.08f,cx+size*.30f,cy+size*.30f,p);
        p.setStyle(Paint.Style.FILL);
    }

    private boolean drawerOpen = false;

    private void showDrawer() {
        final LinearLayout panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(18, 12, 18, 12);

        TextView title = new TextView(service);
        title.setText("امکانات");
        title.setTextSize(20);
        title.setTextColor(BLACK);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, 54));

        ScrollView scroll = new ScrollView(service);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(service);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button transparency = drawerButton("شفافیت کیبورد");
        Button palette = drawerButton("رنگ کیبورد");
        Button emoji = drawerButton("انتخاب Emoji");
        Button steering = drawerButton("فرمان ماشین");
        Button arabic = drawerButton("حرکت‌ها و صداهای عربی");
        Button history = drawerButton("تاریخچه کلیپ‌بورد (۱۰۰)");
        Button magnifierButton = drawerButton("ذره‌بین");
        Button resize = drawerButton("Resize / Float");
        Button mouse = drawerButton("موس صفحه وب");
        Button calculator = drawerButton("ماشین حساب");
        Button command = drawerButton("Command");

        Button quickSettings = drawerButton("Quick Settings");
        Button[] buttons={transparency,palette,emoji,steering,arabic,history,magnifierButton,resize,mouse,calculator,command,quickSettings};
        for(Button b:buttons) list.addView(b);

        final PopupWindow popup = new PopupWindow(panel,
                Math.min((int)(Math.max(320,getWidth()) * 0.94f), 700),
                Math.min(dp(520), Math.max(dp(260), getHeight() - dp(12))), false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setOutsideTouchable(true);
        popup.setTouchable(true);
        popup.setFocusable(false); // Do not steal focus from the editor / close the IME.
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popup.setElevation(8f);

        transparency.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showTransparency));
        palette.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showColorPalette));
        emoji.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showEmojiPicker));
        steering.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showSteeringWheel));
        arabic.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showArabicHarakat));
        history.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showClipboardHistory));
        magnifierButton.setOnClickListener(v -> {
            popup.dismiss();
            service.launchScreenMagnifier();
        });
        resize.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showResizeFloatInfo));
        mouse.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showMouseControls));
        calculator.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showCalculator));
        command.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showCommand));
        quickSettings.setOnClickListener(v -> { popup.dismiss(); service.requestQuickSettingsTiles(); });

        popup.showAtLocation(this, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(6));
        drawerOpen = true;
        popup.setOnDismissListener(() -> drawerOpen = false);
    }

    private void showCommand() {
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackgroundColor(Color.WHITE);

        final Button[] closeHolder = new Button[1];
        addPopupHeader(root, "Command", closeHolder);

        TextView info = new TextView(service);
        info.setText("فرمان‌های سریع صفحه‌کلید");
        info.setTextSize(16);
        info.setTextColor(BLACK);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(6), dp(6), dp(6), dp(10));
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        String[][] commands = {
                {"Copy All", "copyall", "Paste", "paste"},
                {"Copy Screen", "copyscreen", "Cut", "cut"},
                {"Undo", "undo", "Redo", "redo"},
                {"Backspace", "backspace", "Enter", "enter"},
                {"← Left", "left", "Right →", "right"},
                {"↑ Up", "up", "↓ Down", "down"},
                {"Space", "space", "Delete", "delete"}
        };

        for (String[] rowData : commands) {
            LinearLayout row = new LinearLayout(service);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(2), 0, dp(2));
            for (int i = 0; i < rowData.length; i += 2) {
                Button b = drawerButton(rowData[i]);
                String command = rowData[i + 1];
                b.setOnClickListener(v -> executeCommand(command));
                row.addView(b, new LinearLayout.LayoutParams(0, dp(50), 1f));
            }
            root.addView(row, new LinearLayout.LayoutParams(-1, dp(54)));
        }

        final PopupWindow popup = new PopupWindow(root,
                Math.min(dp(500), Math.max(dp(320), getWidth() - dp(16))),
                WindowManager.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setTouchable(true);
        popup.setFocusable(false);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popup.setElevation(10f);
        closeHolder[0].setOnClickListener(v -> popup.dismiss());
        popup.showAtLocation(this, Gravity.CENTER, 0, 0);
    }

    private boolean executeCommand(String cmd) {
        if (cmd == null || cmd.isEmpty()) return false;
        if (cmd.equals("copy") || cmd.equals("copyall")) { service.copyAll(); return true; }
        if (cmd.equals("copyscreen")) { service.copyScreen(); return true; }
        if (cmd.equals("paste")) { service.paste(); return true; }
        if (cmd.equals("cut")) { service.cut(); return true; }
        if (cmd.equals("undo")) { service.undo(); return true; }
        if (cmd.equals("redo")) { service.redo(); return true; }
        if (cmd.equals("backspace") || cmd.equals("delete")) { service.backspace(); return true; }
        if (cmd.equals("enter")) { service.enter(); return true; }
        if (cmd.equals("left")) { service.moveCursorHorizontal(-1); return true; }
        if (cmd.equals("right")) { service.moveCursorHorizontal(1); return true; }
        if (cmd.equals("up")) { service.move(android.view.KeyEvent.KEYCODE_DPAD_UP); return true; }
        if (cmd.equals("down")) { service.move(android.view.KeyEvent.KEYCODE_DPAD_DOWN); return true; }
        if (cmd.equals("space")) { service.type(" "); return true; }
        return false;
    }

    private void showCalculator() {
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackgroundColor(Color.WHITE);

        TextView display = new TextView(service);
        display.setText("0");
        display.setTextSize(26);
        display.setTextColor(BLACK);
        display.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        display.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable dg = new GradientDrawable();
        dg.setColor(Color.rgb(245,245,242));
        dg.setStroke(1, Color.LTGRAY);
        dg.setCornerRadius(dp(6));
        display.setBackground(dg);
        root.addView(display, new LinearLayout.LayoutParams(-1, dp(52)));

        final double[] stored = {0};
        final char[] operation = {' '};
        final boolean[] entering = {false};

        String[][] keys = {{"C","⌫","÷","×"},{"7","8","9","-"},{"4","5","6","+"},{"1","2","3","="},{"0",".","", ""}};
        for (String[] row : keys) {
            LinearLayout line = new LinearLayout(service);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (String k : row) {
                if (k.isEmpty()) {
                    line.addView(new Space(service), new LinearLayout.LayoutParams(0, dp(48), 1));
                    continue;
                }
                Button btn = new Button(service);
                btn.setText(k);
                btn.setTextSize(17);
                btn.setTextColor(NAVY);
                btn.setAllCaps(false);
                btn.setPadding(0, 0, 0, 0);
                btn.setOnClickListener(v -> {
                    String z = ((Button)v).getText().toString();
                    String cur = display.getText().toString();
                    if (z.equals("C")) {
                        stored[0] = 0; operation[0] = ' '; entering[0] = false;
                        display.setText("0");
                        return;
                    }
                    if (z.equals("⌫")) {
                        if (cur.length() > 1) display.setText(cur.substring(0, cur.length()-1));
                        else display.setText("0");
                        return;
                    }
                    if (z.equals(".") && cur.contains(".")) return;
                    if (z.equals("+") || z.equals("-") || z.equals("×") || z.equals("÷")) {
                        try { stored[0] = Double.parseDouble(cur); } catch (Exception ex) { stored[0] = 0; }
                        operation[0] = z.charAt(0);
                        entering[0] = true;
                        return;
                    }
                    if (z.equals("=")) {
                        if (operation[0] == ' ') return;
                        try {
                            double right = Double.parseDouble(cur);
                            double result;
                            switch (operation[0]) {
                                case '+': result = stored[0] + right; break;
                                case '-': result = stored[0] - right; break;
                                case '×': result = stored[0] * right; break;
                                case '÷': if (right == 0) throw new ArithmeticException(); result = stored[0] / right; break;
                                default: return;
                            }
                            String out = (result == Math.rint(result)) ? Long.toString((long)result) : Double.toString(result);
                            display.setText(out);
                            stored[0] = result;
                            operation[0] = ' ';
                            entering[0] = true;
                        } catch (Exception ex) {
                            display.setText("خطا");
                            stored[0] = 0; operation[0] = ' '; entering[0] = false;
                        }
                        return;
                    }
                    if (entering[0] || cur.equals("خطا")) {
                        display.setText(z.equals(".") ? "0." : z);
                        entering[0] = false;
                    } else {
                        display.setText(cur.equals("0") ? z : cur + z);
                    }
                });
                line.addView(btn, new LinearLayout.LayoutParams(0, dp(48), 1));
            }
            root.addView(line, new LinearLayout.LayoutParams(-1, dp(48)));
        }

        final PopupWindow calc = new PopupWindow(root, dp(320), WindowManager.LayoutParams.WRAP_CONTENT, true);
        calc.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        calc.setOutsideTouchable(true);
        calc.setElevation(dp(8));
        Button close = new Button(service);
        close.setText("بستن");
        close.setTextColor(NAVY);
        close.setAllCaps(false);
        close.setOnClickListener(v -> calc.dismiss());
        root.addView(close, new LinearLayout.LayoutParams(-1, dp(44)));
        calc.showAtLocation(this, Gravity.CENTER, 0, 0);
    }

    private void showAndKeepKeyboard(PopupWindow popup, Runnable action) {
        popup.dismiss();
        postDelayed(action, 80);
    }

    private Button drawerButton(String text) {
        Button b = new Button(service);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(NAVY);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58);
        lp.setMargins(0, 3, 0, 3);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout addPopupHeader(LinearLayout root, String titleText, Button[] closeHolder) {
        LinearLayout bar = new LinearLayout(service);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(2, 0, 2, 0);
        Button close = drawerButton("×"); close.setTextSize(19);
        Button space = drawerButton("Space"); space.setTextSize(13);
        Button back = drawerButton("Backspace"); back.setTextSize(12);
        styleHeaderButton(close, Color.rgb(205, 45, 45), Color.WHITE);
        styleHeaderButton(space, BLUE, Color.WHITE);
        styleHeaderButton(back, BLUE, Color.WHITE);
        TextView title = new TextView(service);
        title.setText(titleText); title.setTextSize(16); title.setTextColor(NAVY); title.setGravity(Gravity.CENTER);
        bar.addView(close, new LinearLayout.LayoutParams(dp(42), dp(36)));
        bar.addView(space, new LinearLayout.LayoutParams(dp(70), dp(36)));
        bar.addView(back, new LinearLayout.LayoutParams(dp(88), dp(36)));
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1f));
        space.setOnClickListener(v -> service.type(" "));
        back.setOnClickListener(v -> service.backspace());
        if (closeHolder != null && closeHolder.length > 0) closeHolder[0] = close;
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(40)));
        return bar;
    }

    private void styleHeaderButton(Button b, int background, int foreground) {
        b.setTextColor(foreground);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(background);
        gd.setCornerRadius(dp(6));
        b.setBackground(gd);
        b.setPadding(0, 0, 0, 0);
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private void applyKeyboardHeightDp(int heightDp) {
        int clamped = Math.max(minHeightDp, Math.min(maxHeightDp, heightDp));
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) lp = new android.view.ViewGroup.LayoutParams(-1, dp(clamped));
        lp.height = dp(clamped);
        lp.width = -1;
        setLayoutParams(lp);
        requestLayout();
        invalidate();
        service.getSharedPreferences("keyforge_alphaone_settings", 0).edit()
                .putInt("keyboard_height_dp", clamped).apply();
    }

    private int savedKeyboardHeightDp() {
        return service.getSharedPreferences("keyforge_alphaone_settings", 0)
                .getInt("keyboard_height_dp", defaultHeightDp);
    }

    private void enterResizeMode() {
        resizeMode = true;
        postInvalidateOnAnimation();
    }

    private void exitResizeMode() {
        resizeMode = false;
        resizingKeyboard = false;
        invalidate();
    }

    private void showResizeFloatInfo() {
        ScrollView root=new ScrollView(service);
        LinearLayout content=new LinearLayout(service);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24,16,24,16);
        root.addView(content, new ScrollView.LayoutParams(-1,-2));

        final Button[] headerClose = new Button[1];
        addPopupHeader(content, "Resize / Float", headerClose);

        TextView info=new TextView(service);
        info.setText("برای تغییر اندازه، ابتدا «تغییر اندازه» را بزنید و سپس گوشه پایین‌راست کیبورد را بکشید.\nدکمه «بازگشت به اندازه پیش‌فرض» اندازه اولیه را برمی‌گرداند.");
        info.setTextSize(16);
        content.addView(info,new LinearLayout.LayoutParams(-1,110));

        SeekBar sizeBar=new SeekBar(service);
        sizeBar.setMax(maxHeightDp-minHeightDp);
        int currentSize=Math.max(minHeightDp,Math.min(maxHeightDp,savedKeyboardHeightDp()));
        sizeBar.setProgress(currentSize-minHeightDp);
        content.addView(sizeBar,new LinearLayout.LayoutParams(-1,60));
        TextView sizeValue=new TextView(service);
        sizeValue.setText(currentSize+" dp");
        sizeValue.setGravity(Gravity.CENTER);
        content.addView(sizeValue,new LinearLayout.LayoutParams(-1,42));
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int progress,boolean fromUser){
                int v=minHeightDp+progress;
                sizeValue.setText(v+" dp");
                applyKeyboardHeightDp(v);
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });

        Button resize=new Button(service);
        resize.setText("تغییر اندازه با کشیدن گوشه");
        content.addView(resize);
        Button floatButton=new Button(service);
        floatButton.setText("Float — کیبورد شناور");
        content.addView(floatButton);
        Button stopFloat=new Button(service);
        stopFloat.setText("خاموش کردن Float");
        content.addView(stopFloat);
        Button defaultSizeButton=new Button(service);
        defaultSizeButton.setText("بازگشت به اندازه پیش‌فرض");
        content.addView(defaultSizeButton);
        Button okay=new Button(service);
        okay.setText("Okay");
        content.addView(okay);

        final PopupWindow popup=new PopupWindow(root,
                Math.min(dp(430),Math.max(dp(310),getWidth()-dp(12))),
                Math.min(dp(620),Math.max(dp(300),getHeight()-dp(24))), false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setTouchable(true);
        popup.setFocusable(false);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popup.setElevation(10f);

        headerClose[0].setOnClickListener(v -> popup.dismiss());
        resize.setOnClickListener(v->{ popup.dismiss(); enterResizeMode(); });
        floatButton.setOnClickListener(v->{ popup.dismiss(); service.startFloatingKeyboard(); });
        stopFloat.setOnClickListener(v->{ popup.dismiss(); service.stopFloatingKeyboard(); });
        defaultSizeButton.setOnClickListener(v->{
            service.getSharedPreferences("keyforge_alphaone_settings",0).edit().remove("keyboard_height_dp").apply();
            applyKeyboardHeightDp(defaultHeightDp);
            exitResizeMode();
        });
        okay.setOnClickListener(v->{ exitResizeMode(); popup.dismiss(); });
        popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private void showClipboardHistory() {
        final List<String> items = service.getClipboardHistory();
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 8, 12, 8);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "تاریخچه کلیپ‌بورد — ۱۰۰ مورد آخر", headerClose);
        ScrollView scroll = new ScrollView(service);
        LinearLayout list = new LinearLayout(service); list.setOrientation(LinearLayout.VERTICAL);
        if (items.isEmpty()) {
            TextView empty = new TextView(service); empty.setText("هنوز موردی در تاریخچه نیست"); empty.setGravity(Gravity.CENTER); empty.setTextSize(17);
            list.addView(empty, new LinearLayout.LayoutParams(-1, 80));
        } else {
            for (int i=0;i<items.size();i++) {
                final String item=items.get(i);
                Button b=drawerButton((i+1)+"  "+item.replace("\n"," "));
                b.setOnClickListener(v -> {
                    service.pasteHistory(item);
                });
                list.addView(b);
            }
        }
        scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        final PopupWindow popup=new PopupWindow(root, Math.min(dp(380), Math.max(dp(300), getWidth()-dp(16))), Math.min(dp(620), Math.max(dp(360), getHeight()-dp(16))), false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v -> popup.dismiss());
        popup.showAtLocation(this, Gravity.CENTER, 0, 0);
    }

    private void showColorPalette() {
        final int[] colors = {
                Color.rgb(250,249,244), Color.rgb(255,255,255), Color.rgb(245,245,245),
                Color.rgb(255,244,230), Color.rgb(255,235,235), Color.rgb(235,245,255),
                Color.rgb(235,250,240), Color.rgb(245,238,255), Color.rgb(255,248,205),
                Color.rgb(225,240,235), Color.rgb(235,235,225), Color.rgb(225,230,240)
        };
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(14,8,14,8);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "رنگ کیبورد", headerClose);
        GridLayout grid=new GridLayout(service); grid.setColumnCount(4);
        for(int color:colors){
            Button b=new Button(service); b.setText(""); b.setBackgroundColor(color);
            b.setOnClickListener(v->{ KEY=color; service.getSharedPreferences("keyforge_alphaone_settings",0).edit().putInt("keyboard_key_color",color).apply(); invalidate(); });
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=70; lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(4,4,4,4); grid.addView(b,lp);
        }
        root.addView(grid,new LinearLayout.LayoutParams(-1,250));
        PopupWindow popup=new PopupWindow(root,Math.min(dp(380),Math.max(dp(300),getWidth()-dp(16))),Math.min(dp(430),Math.max(dp(330),getHeight()-dp(16))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private void showTransparency() {
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,10,28,10);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "شفافیت کیبورد", headerClose);
        SeekBar bar=new SeekBar(service); bar.setMax(99); int current=Math.max(1,Math.min(100,Math.round(getAlpha()*100f))); bar.setProgress(current-1); root.addView(bar,new LinearLayout.LayoutParams(-1,56));
        TextView value=new TextView(service); value.setText(current+"%"); value.setTextSize(17); value.setTextColor(BLACK); value.setGravity(Gravity.CENTER); root.addView(value,new LinearLayout.LayoutParams(-1,48));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar b,int progress,boolean fromUser){int v=progress+1; value.setText(v+"%"); setAlpha(v/100f); service.getSharedPreferences("keyforge_alphaone_settings",0).edit().putInt("keyboard_alpha",v).apply();} public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){} });
        PopupWindow popup=new PopupWindow(root,Math.min(dp(380),Math.max(dp(300),getWidth()-dp(16))),Math.min(dp(300),Math.max(dp(260),getHeight()-dp(16))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private void showEmojiPicker() {
        final String[] emojis = {
                "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗","🤔","🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😪","😵","🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕","🤠","🥸","😈","👿","👹","👺","💀","☠️","👻","👽","🤖","🎃","😺","😸","😹","😻","😼","😽","🙀","😿","😾",
                "❤️","🩷","🧡","💛","💚","🩵","💙","💜","🤎","🖤","🩶","🤍","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","💯","💢","💥","💦","💨","💫","💬","🗨️","🗯️","💭","💤",
                "👍","👎","👏","🙌","🙏","🤝","👌","✌️","🤞","🤟","🤘","🤙","👋","💪","👊","✊","👉","👈","☝️","👇","👆","✍️","💅","🫶","🤲","🙋",
                "🔥","⭐","✨","🎉","🎊","✅","❌","⚡","🌹","🌸","🌺","🌻","🌼","🌷","🌱","🌿","🍀","☘️","🍁","🍂","🍃","🌞","🌝","🌚","🌙","🌟","💫","☀️","🌈","☁️","❄️","☃️","🌊",
                "🍎","🍏","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍒","🍑","🍍","🥝","🥑","🍅","🥕","🌽","🍞","🧀","🍔","🍟","🍕","🌭","🍿","🍩","🍪","🍰","🎂","🍫","🍭","🍬","🍯","🥭","🥥","🥨","🍗","🍖","🌮","🌯","🍜","🍣","🍱","🥗","🍦","☕","🍵","🧃","🥤","🧋",
                "⚽","🏀","🏈","⚾","🎾","🏐","🎱","🏆","🥇","🥈","🥉","🎯","🎮","🎲","🧩","🎸","🎹","🎺","🥁","🎻","🎬","🎨","🎈","🎁","🎀","🎟️","🎫",
                "🚗","🚕","🚙","🚌","🚓","🚑","🚒","🚜","🚲","🛵","🏍️","✈️","🚀","🚢","⛵","🚉","🚇","🚦","🛑","🏠","🏢","🏫","🏥","🏰","🗽","⛺",
                "⌚","📱","💻","🖥️","⌨️","🖨️","📷","🎥","🎧","🎤","📚","✏️","📝","📌","📎","🔒","🔑","💡","🔔","⚙️","🔍","🔎","🔧","🔨","🪛","🧰","🔩","🧲","🔬","🔭","💊","🩺"
        };
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(8,8,8,8);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "انتخاب Emoji", headerClose);
        ScrollView scroll=new ScrollView(service);
        LinearLayout all=new LinearLayout(service); all.setOrientation(LinearLayout.VERTICAL);
        GridLayout grid=new GridLayout(service); grid.setColumnCount(8); grid.setPadding(6,6,6,6);
        for(String emoji:emojis) addEmojiButton(grid, emoji);
        all.addView(grid, new LinearLayout.LayoutParams(-1,-2));
        TextView countryTitle=new TextView(service); countryTitle.setText("پرچم کشورها"); countryTitle.setTextColor(NAVY); countryTitle.setTextSize(15); countryTitle.setGravity(Gravity.CENTER);
        all.addView(countryTitle,new LinearLayout.LayoutParams(-1,dp(34)));
        GridLayout countryGrid=new GridLayout(service); countryGrid.setColumnCount(8); countryGrid.setPadding(4,2,4,2);
        addCountryFlags(countryGrid);
        all.addView(countryGrid,new LinearLayout.LayoutParams(-1,-2));
        scroll.addView(all,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        final PopupWindow popup=new PopupWindow(root,Math.min((int)(getWidth()*0.96f),720),Math.min(dp(620),Math.max(dp(380),getHeight()-dp(10))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss());
        popup.showAtLocation(this,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,dp(6));
    }

    private void addCountryFlags(GridLayout grid) {
        String[] countries={"AF","AL","DZ","AS","AD","AO","AI","AQ","AG","AR","AM","AW","AU","AT","AZ","BS","BH","BD","BB","BY","BE","BZ","BJ","BM","BT","BO","BQ","BA","BW","BV","BR","IO","BN","BG","BF","BI","CV","KH","CM","CA","KY","CF","TD","CL","CN","CX","CC","CO","KM","CG","CD","CK","CR","CI","HR","CU","CW","CY","CZ","DK","DJ","DM","DO","EC","EG","SV","GQ","ER","EE","SZ","ET","FK","FO","FJ","FI","FR","GF","PF","TF","GA","GM","GE","DE","GH","GI","GR","GL","GD","GP","GU","GT","GG","GN","GW","GY","HT","HN","HK","HU","IS","IN","ID","IR","IQ","IE","IM","IL","IT","JM","JP","JE","JO","KZ","KE","KI","KP","KR","KW","KG","LA","LV","LB","LS","LR","LY","LI","LT","LU","MO","MG","MW","MY","MV","ML","MT","MH","MQ","MR","MU","YT","MX","FM","MD","MC","MN","ME","MS","MA","MZ","MM","NA","NR","NP","NL","NC","NZ","NI","NE","NG","NU","NF","MK","MP","NO","OM","PK","PW","PS","PA","PG","PY","PE","PH","PN","PL","PT","PR","QA","RE","RO","RU","RW","BL","SH","KN","LC","MF","PM","VC","WS","SM","ST","SA","SN","RS","SC","SL","SG","SX","SK","SI","SB","SO","ZA","GS","SS","ES","LK","SD","SR","SJ","SE","CH","SY","TW","TJ","TZ","TH","TL","TG","TK","TO","TT","TN","TR","TM","TC","TV","UG","UA","AE","GB","US","UM","UY","UZ","VU","VE","VN","VG","VI","WF","EH","YE","ZM","ZW"};
        for(String code:countries) addEmojiButton(grid,flagFromCode(code));
    }

    private void addColoredFolderButton(GridLayout grid, int color) {
        final FolderEmojiView folder = new FolderEmojiView(service, color);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(56); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(2,2,2,2); grid.addView(folder,lp);
        folder.setOnClickListener(v -> service.typeUnit("📁"));
    }

    private class FolderEmojiView extends View {
        private final Paint fp=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        FolderEmojiView(Context c,int color){super(c);this.color=color;setContentDescription("📁");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();float l=w*.18f,r=w*.82f,t=h*.28f,b=h*.72f;fp.setColor(color);fp.setStyle(Paint.Style.FILL);Path path=new Path();path.moveTo(l,t+h*.06f);path.lineTo(l+w*.20f,t+h*.06f);path.lineTo(l+w*.28f,t);path.lineTo(l+w*.48f,t);path.lineTo(l+w*.55f,t+h*.10f);path.lineTo(r,t+h*.10f);path.lineTo(r,b);path.lineTo(l,b);path.close();c.drawPath(path,fp);fp.setColor(Color.argb(70,255,255,255));c.drawRect(l+w*.06f,t+h*.16f,r-w*.06f,t+h*.22f,fp);}
    }



    private String flagFromCode(String code) {
        if (code == null || code.length() != 2) return "";
        code = code.toUpperCase(Locale.US);
        int a = code.charAt(0) - 'A' + 0x1F1E6;
        int b = code.charAt(1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(a)) + new String(Character.toChars(b));
    }

    private void addEmojiButton(GridLayout grid, String emoji) {
        Button b=new Button(service); b.setText(emoji); b.setTextSize(24); b.setAllCaps(false); b.setPadding(0,0,0,0); b.setGravity(Gravity.CENTER);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(48); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(1,1,1,1); grid.addView(b,lp);
        b.setOnClickListener(v -> service.typeUnit(emoji));
    }

    private void showSteeringWheel() {
        final SteeringView wheel = new SteeringView(service);
        final PopupWindow popup = new PopupWindow(wheel, 360, 430, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setOutsideTouchable(false);
        popup.setElevation(12f);
        wheel.setPopup(popup);
        popup.showAtLocation(this, Gravity.CENTER, 0, 0);
    }

    private class SteeringView extends View {
        private final Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private PopupWindow popup;
        private float cx, cy, radius;
        private float downX, downY, startX, startY;
        private boolean resizing;
        private long lastMove;
        private RectF upRect = new RectF(), downRect = new RectF(), leftRect = new RectF(), rightRect = new RectF();
        private RectF closeRect = new RectF(), spaceRect = new RectF(), backRect = new RectF(), padRect = new RectF();

        SteeringView(android.content.Context c) {
            super(c);
            setBackgroundColor(Color.WHITE);
        }

        void setPopup(PopupWindow p) { popup = p; }

        private void button(Canvas c, RectF r, String label) {
            wp.setStyle(Paint.Style.FILL);
            wp.setColor(Color.rgb(245,245,245));
            c.drawRoundRect(r, 14, 14, wp);
            wp.setStyle(Paint.Style.STROKE);
            wp.setStrokeWidth(2);
            wp.setColor(NAVY);
            c.drawRoundRect(r, 14, 14, wp);
            wp.setStyle(Paint.Style.FILL);
            wp.setTextAlign(Paint.Align.CENTER);
            wp.setTextSize(30);
            wp.setColor(NAVY);
            c.drawText(label, r.centerX(), r.centerY()-(wp.ascent()+wp.descent())/2, wp);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(), h=getHeight();
            wp.setStyle(Paint.Style.FILL);
            wp.setColor(Color.rgb(255,255,255));
            c.drawRect(0,0,w,h,wp);

            // Thin top bar shared with other drawer windows: close + Space + Backspace.
            closeRect.set(w-54, 6, w-8, 40);
            spaceRect.set(w-54-dp(68)-4, 6, w-54-4, 40);
            backRect.set(w-54-dp(68)-4-dp(82)-4, 6, w-54-dp(68)-4, 40);
            wp.setColor(Color.rgb(235,235,235));
            c.drawRoundRect(closeRect, 9, 9, wp); c.drawRoundRect(spaceRect, 9, 9, wp); c.drawRoundRect(backRect, 9, 9, wp);
            wp.setColor(NAVY); wp.setTextSize(15); wp.setTextAlign(Paint.Align.CENTER);
            c.drawText("×", closeRect.centerX(), closeRect.centerY()-(wp.ascent()+wp.descent())/2, wp);
            wp.setTextSize(12); c.drawText("Space", spaceRect.centerX(), spaceRect.centerY()-(wp.ascent()+wp.descent())/2, wp);
            wp.setTextSize(11); c.drawText("Backspace", backRect.centerX(), backRect.centerY()-(wp.ascent()+wp.descent())/2, wp);

            float padSize=Math.min(w-90, Math.max(dp(170), Math.min(dp(210), h-dp(220))));
            float padLeft=(w-padSize)/2f;
            float padTop=72;
            padRect.set(padLeft,padTop,padLeft+padSize,padTop+padSize);
            wp.setColor(Color.rgb(245,247,250));
            c.drawRoundRect(padRect, 22, 22, wp);
            wp.setStyle(Paint.Style.STROKE); wp.setStrokeWidth(3); wp.setColor(NAVY);
            c.drawRoundRect(padRect,22,22,wp); wp.setStyle(Paint.Style.FILL);
            wp.setColor(Color.rgb(210,214,220));
            c.drawLine(padRect.centerX(),padRect.top+18,padRect.centerX(),padRect.bottom-18,wp);
            c.drawLine(padRect.left+18,padRect.centerY(),padRect.right-18,padRect.centerY(),wp);
            wp.setColor(NAVY);
            c.drawCircle(padRect.centerX(),padRect.centerY(),24,wp);

            float bs=58, gapB=10;
            float bx=w/2f-bs/2f;
            upRect.set(bx, padRect.bottom+16, bx+bs, padRect.bottom+16+bs);
            downRect.set(bx, upRect.bottom+gapB, bx+bs, upRect.bottom+gapB+bs);
            leftRect.set(bx-bs-gapB, upRect.top, bx-gapB, upRect.bottom);
            rightRect.set(bx+bs+gapB, upRect.top, bx+2*bs+gapB, upRect.bottom);
            button(c,upRect,"↑"); button(c,downRect,"↓"); button(c,leftRect,"←"); button(c,rightRect,"→");

            wp.setColor(Color.rgb(130,130,130));
            c.drawRect(w-22,h-22,w-4,h-4,wp);
        }

        private void moveBy(float dx, float dy) {
            if(Math.abs(dx)>Math.abs(dy))
                service.move(dx<0?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT);
            else
                service.move(dy<0?KeyEvent.KEYCODE_DPAD_UP:KeyEvent.KEYCODE_DPAD_DOWN);
        }

        private void handleButton(float x,float y){
            if(closeRect.contains(x,y)){ popup.dismiss(); return; }
            if(spaceRect.contains(x,y)){ service.type(" "); return; }
            if(backRect.contains(x,y)){ service.backspace(); return; }
            if(upRect.contains(x,y)){ service.move(KeyEvent.KEYCODE_DPAD_UP); return; }
            if(downRect.contains(x,y)){ service.move(KeyEvent.KEYCODE_DPAD_DOWN); return; }
            if(leftRect.contains(x,y)){ service.move(KeyEvent.KEYCODE_DPAD_LEFT); return; }
            if(rightRect.contains(x,y)){ service.move(KeyEvent.KEYCODE_DPAD_RIGHT); return; }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                handleButton(x,y);
                if(closeRect.contains(x,y)||spaceRect.contains(x,y)||backRect.contains(x,y)||upRect.contains(x,y)||downRect.contains(x,y)||leftRect.contains(x,y)||rightRect.contains(x,y)) return true;
                downX=x; downY=y; startX=getTranslationX(); startY=getTranslationY();
                resizing=(x>getWidth()-35 && y>getHeight()-35);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                if(resizing){
                    int nw=Math.max(300,(int)(getWidth()+x-downX));
                    int nh=Math.max(360,(int)(getHeight()+y-downY));
                    popup.setWidth(nw); popup.setHeight(nh);
                    downX=x; downY=y;
                }else if(padRect.contains(x,y)){
                    moveBy(x-downX,y-downY);
                    downX=x; downY=y;
                }else{
                    setTranslationX(startX+x-downX);
                    setTranslationY(startY+y-downY);
                }
                invalidate();
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                resizing=false;
                return true;
            }
            return true;
        }
    }

    private void showArabicHarakat() {
        final String[][] groups = {
                {"َ","فتحه — کوتاه a"},{"ِ","کسره — کوتاه i"},{"ُ","ضمه — کوتاه u"},{"ْ","سکون"},{"ّ","تشدید"},
                {"ً","تنوین فتح"},{"ٍ","تنوین کسر"},{"ٌ","تنوین ضم"},{"ٓ","مدّ"},{"ٰ","الف خنجری"},{"ٔ","همزه بالا"},{"ٕ","همزه پایین"},
                {"َا","بلند آ / ا"},{"ِی","بلند ای / ی"},{"ُو","بلند او / و"},{"آ","الف مدّی"},{"ٱ","الف وصل"},{"ء","همزه"},
                {"أ","الف همزه بالا"},{"إ","الف همزه پایین"},{"ؤ","واو همزه"},{"ئ","ی همزه"},{"ـ","کشیده"},{"ٖ","نشان زیر"},{"ٗ","نشان بالا"},
                {"ۡ","سکون کوچک"},{"ۢ","اقلاب"},{"ۭ","مدّ لازم"},{"ۥ","واو کوچک"},{"ۦ","یای کوچک"},{"ۨ","نشان بینی"},{"۫","ضبط بالا"},{"۬","ضبط پایین"},
                {"ۚ","وقف جواز"},{"ۖ","وقف لازم"},{"ۗ","وقف قلی"},{"ۙ","وقف مطلق"},{"ۘ","وقف معانقه"},{"ۛ","وقف تعانق"},{"ۜ","سجده"},{"۟","علامت قرائت"},
                {"اَ","نمونه فتحه"},{"اِ","نمونه کسره"},{"اُ","نمونه ضمه"},{"با","نمونه صدای بلند آ"},{"بی","نمونه صدای بلند ای"},{"بو","نمونه صدای بلند او"}
        };
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(14,8,14,8);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "حرکت‌ها و صداهای عربی — کوتاه و بلند", headerClose);
        GridLayout grid=new GridLayout(service); grid.setColumnCount(4);
        for(String[] item:groups){
            Button b=new Button(service); b.setText(item[0]+"\n"+item[1]); b.setTextSize(14); b.setTextColor(NAVY); b.setAllCaps(false);
            final String mark=item[0]; b.setOnClickListener(v->service.typeUnit(mark));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=72; lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(3,3,3,3); grid.addView(b,lp);
        }
        root.addView(grid,new LinearLayout.LayoutParams(-1,0,1f));
        PopupWindow popup=new PopupWindow(root,Math.min(dp(400),Math.max(dp(310),getWidth()-dp(12))),Math.min(dp(620),Math.max(dp(400),getHeight()-dp(12))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }



    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        gap=dp(4);
        keyH=Math.max(1f,h/7f);
        suggestionH=keyH*0.62f;
        drawKeyboard(c);
        if (resizeMode) {
            p.setColor(NAVY); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3);
            float g = Math.min(42f, Math.min(w, h) * 0.10f);
            c.drawLine(w - g, h - 7, w - 7, h - g, p);
            c.drawLine(w - g - 7, h - 7, w - 7, h - g - 7, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private float[] visibleRowBounds(){
        float total=0f;
        for(int i=0;i<7;i++) if(!((i==0&&hideTopRow)||(i==1&&hideSuggestionRow))) total += SOURCE_BANDS[i+1]-SOURCE_BANDS[i];
        float[] out=new float[8]; float y=0f; out[0]=0f;
        for(int i=0;i<7;i++){
            if((i==0&&hideTopRow)||(i==1&&hideSuggestionRow)){ out[i+1]=y; continue; }
            y += (SOURCE_BANDS[i+1]-SOURCE_BANDS[i])/total; out[i+1]=y;
        }
        return out;
    }

    private void drawKeyboard(Canvas c){
        float w=getWidth(), h=getHeight();
        if(referenceKeyboard==null) return;
        float[] bounds=visibleRowBounds();
        for(int i=0;i<7;i++){
            if((i==0&&hideTopRow)||(i==1&&hideSuggestionRow)) continue;
            Rect src=new Rect(0,Math.round(SOURCE_BANDS[i]),referenceKeyboard.getWidth(),Math.round(SOURCE_BANDS[i+1]));
            RectF dst=new RectF(0,bounds[i]*h,w,bounds[i+1]*h);
            p.setAlpha(255); p.setFilterBitmap(true); p.setStyle(Paint.Style.FILL);
            c.drawBitmap(referenceKeyboard,src,dst,p);
        }
        if(englishMode) drawEnglishRows(c,bounds);
    }

    private void drawEnglishRows(Canvas c,float[] bounds){
        String[][] rows={
            {"1","2","3","4","5","6","7","8","9","0","-","="},
            {"Q","W","E","R","T","Y","U","I","O","P","[","]","\\"},
            {"Caps","A","S","D","F","G","H","J","K","L",";","'",""},
            {"Z","X","C","V","B","N","M",",",".","/","?","",""}
        };
        int[] logical={2,3,4,5};
        for(int z=0;z<logical.length;z++){
            int row=logical[z];
            float y0=bounds[row]*getHeight(), y1=bounds[row+1]*getHeight();
            float left=dp(4), right=getWidth()-dp(4);
            boolean reserveRight=(row==2 || row==3 || row==4);
            if(reserveRight) right=getWidth()*.914f;
            int count=rows[z].length;
            float ww=(right-left-dp(4)*(count-1))/count;
            for(int i=0;i<count;i++){
                float l=left+i*(ww+dp(4));
                key(c,l,y0,l+ww,y1,rows[z][i],NAVY,false);
            }
            if(row==3 || row==4){
                float enterL=getWidth()*.914f;
                keyWithBackground(c,enterL,y0,getWidth()-dp(4),y1,"Enter",NAVY,ENTER_BG,false);
            }
        }
    }

    private void drawCapsAndMagnifierRow(Canvas c,float y){}

    private void drawMicrophone(Canvas c,float l,float t,float r,float b){
        float cx=(l+r)/2f, cy=(t+b)/2f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3f,keyH*.09f)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(NAVY);
        c.drawRoundRect(cx-keyH*.13f, cy-keyH*.28f, cx+keyH*.13f, cy+keyH*.10f, keyH*.13f, keyH*.13f, p);
        c.drawArc(cx-keyH*.25f, cy-keyH*.10f, cx+keyH*.25f, cy+keyH*.32f, 0, 180, false, p);
        c.drawLine(cx, cy+keyH*.30f, cx, cy+keyH*.43f, p);
        c.drawLine(cx-keyH*.15f, cy+keyH*.43f, cx+keyH*.15f, cy+keyH*.43f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawMousePointer(Canvas c,float l,float t,float r,float b){
        float cx=l+(r-l)*.50f;
        float cy=t+(b-t)*.50f;
        float s=Math.min(r-l,b-t)*.34f;
        Path pointer=new Path();
        pointer.moveTo(cx-s*.65f, cy-s);
        pointer.lineTo(cx-s*.65f, cy+s*.72f);
        pointer.lineTo(cx-s*.08f, cy+s*.30f);
        pointer.lineTo(cx+s*.22f, cy+s*.92f);
        pointer.lineTo(cx+s*.50f, cy+s*.72f);
        pointer.lineTo(cx+s*.20f, cy+s*.12f);
        pointer.lineTo(cx+s*.82f, cy+s*.12f);
        pointer.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(NAVY);
        c.drawPath(pointer,p);
    }

    private void row(Canvas c,float y,float[] weights,String[] labels){
        float total=0;
        for(float q:weights)total+=q;
        float ww=(getWidth()-gap*(weights.length+1))/total,x=gap;
        for(int i=0;i<weights.length;i++){
            float cw=ww*weights[i];
            String s=labels[i];
            if(s.contains("\n")){
                key(c,x,y,x+cw,y+keyH,"",NAVY,false);
                String[] a=s.split("\\n");
                txt(c,a[0],x+cw/2,y+keyH*.35f,Math.min(20,keyH*.3f),NAVY);
                txt(c,a[1],x+cw/2,y+keyH*.7f,Math.min(20,keyH*.3f),NAVY);
            }else{
                key(c,x,y,x+cw,y+keyH,s,NAVY,false);
            }
            x+=cw+gap;
        }
    }

    private void rowFrom(Canvas c,float y,float left,String[] labels){
        float x=left+gap,available=getWidth()-left-gap;
        float ww=(available-gap*(labels.length+1))/labels.length;
        for(String s:labels){
            key(c,x,y,x+ww,y+keyH,s,BLUE,false);
            x+=ww+gap;
        }
    }

    private void rowFromRightReserved(Canvas c,float y,float left,String[] labels,float reservedRight){
        float x=left+gap;
        float available=getWidth()-left-reservedRight-gap;
        float ww=(available-gap*(labels.length+1))/labels.length;
        for(String s:labels){
            if(s.contains("\n")){
                key(c,x,y,x+ww,y+keyH,"",BLUE,false);
                String[] a=s.split("\n");
                txt(c,a[0],x+ww/2,y+keyH*.35f,Math.min(20,keyH*.3f),NAVY);
                txt(c,a[1],x+ww/2,y+keyH*.7f,Math.min(20,keyH*.3f),NAVY);
            } else key(c,x,y,x+ww,y+keyH,s,BLUE,false);
            x+=ww+gap;
        }
    }

    private void rowFromRightReservedWithEscape(Canvas c,float y,float left,String[] labels,float reservedRight){
        float x=left+gap;
        float available=getWidth()-left-reservedRight-gap;
        float ww=(available-gap*(labels.length+1))/labels.length;
        for(String s:labels){
            if(s.contains("\n")){
                key(c,x,y,x+ww,y+keyH,"",BLUE,false);
                String[] a=s.split("\n");
                txt(c,a[0],x+ww/2,y+keyH*.35f,Math.min(18,keyH*.28f),NAVY);
                txt(c,a[1],x+ww/2,y+keyH*.70f,Math.min(18,keyH*.28f),NAVY);
            } else {
                // Draw the third alphabet row only once so its glyphs have the same stroke weight as the other alphabet rows.
                key(c,x,y,x+ww,y+keyH,s,BLUE,false);
            }
            x+=ww+gap;
        }
    }

    private void drawArrow(Canvas c,float x,float y,float ww,float hh,String s){
        key(c,x,y,x+ww,y+hh,"",NAVY,true);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(hh*.62f); p.setColor(BLUE); p.setTextAlign(Paint.Align.CENTER);
        p.setStyle(Paint.Style.FILL); c.drawText(s,x+ww/2,y+hh/2-(p.ascent()+p.descent())/2,p);
    }

    private void drawPressGlow(Canvas c){
        // Keep the press light exactly inside the same rounded shape as the key.
        float inset = Math.max(1f, keyH * .018f);
        float l = pressL + inset, t = pressT + inset, r = pressR - inset, b = pressB - inset;
        float radius = Math.max(2f, Math.min(7f, keyH * .025f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(145, 255, 190, 0));
        c.save();
        Path clip = new Path();
        clip.addRoundRect(new RectF(l, t, r, b), radius, radius, Path.Direction.CW);
        c.clipPath(clip);
        c.drawRoundRect(l, t, r, b, radius, radius, p);
        c.restore();
    }

    public void refreshSuggestions(){
        CharSequence q=service.getCurrentInputConnection()==null?null:
                service.getCurrentInputConnection().getTextBeforeCursor(80,0);
        String word="";
        if(q!=null){
            String b=q.toString();
            int i=b.length()-1;
            while(i>=0&&!Character.isWhitespace(b.charAt(i)))i--;
            word=b.substring(i+1);
        }
        Arrays.fill(suggestions,"");
        if(word.length()==0){invalidate();return;}
        int n=0;
        for(String[] group:WORDS)
            for(String x:group)
                if(x.startsWith(word)&&!x.equals(word)&&n<6)suggestions[n++]=x;
        if(n==0)
            for(String[] group:WORDS)
                for(String x:group)
                    if(x.contains(word)&&n<6)suggestions[n++]=x;
        invalidate();
    }

    private void setPressGlow(float l, float t, float r, float b, boolean held){
        pressL=l; pressT=t; pressR=r; pressB=b;
        pressHeld=held;
        pressGlow=true;
        handler.removeCallbacks(clearPressGlow);
        if (!held) handler.postDelayed(clearPressGlow, 140);
        invalidate();
    }

    private void clearPressGlowNow(){
        pressHeld=false;
        pressGlow=false;
        handler.removeCallbacks(clearPressGlow);
        invalidate();
    }

    private int getRowAt(float y){
        if(y<0 || y>getHeight()) return -1;
        float[] b=visibleRowBounds();
        for(int i=0;i<7;i++){
            if((i==0&&hideTopRow)||(i==1&&hideSuggestionRow)) continue;
            if(y>=b[i]*getHeight() && y<=b[i+1]*getHeight()) return i;
        }
        return -1;
    }

    private float rowTop(int row){ return visibleRowBounds()[row]*getHeight(); }

    private void pressRectFor(float x,float y,boolean held){
        int row=getRowAt(y);
        if(row<0){clearPressGlowNow();return;}
        float[] b=visibleRowBounds();
        setPressGlow(0,b[row]*getHeight(),getWidth(),b[row+1]*getHeight(),held);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        float x = e.getX(), y = e.getY();

        if (resizeMode) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float grip = Math.max(42f, dp(34));
                if ((x >= getWidth() - grip && y >= getHeight() - grip) || y >= getHeight() - dp(70)) {
                    resizingKeyboard = true;
                    resizeStartY = y;
                    resizeStartHeight = getHeight();
                    return true;
                }
                // In resize mode, taps outside the grip do not activate keyboard keys.
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE && resizingKeyboard) {
                int newPx = resizeStartHeight + Math.round(y - resizeStartY);
                int newDp = Math.round(newPx / getResources().getDisplayMetrics().density);
                applyKeyboardHeightDp(newDp);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                resizingKeyboard = false;
                return true;
            }
            return true;
        }

        if(e.getAction()==MotionEvent.ACTION_DOWN){
            stopRepeat();
            pressRectFor(x, y, true);
            handle(x,y);
            if (isRepeatableSymbolAt(x, y)) startSymbolRepeat(x, y);
            // A normal tap keeps its light briefly; Backspace keeps it lit while held.
            if (!isBackspaceAt(x, y)) {
                pressHeld=false;
                handler.removeCallbacks(clearPressGlow);
                handler.postDelayed(clearPressGlow, 140);
            }
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
            stopRepeat();
            clearPressGlowNow();
        }
        return true;
    }

    private boolean isRepeatableSymbolAt(float x,float y){
        int row=getRowAt(y);
        if(row==2){ return x < getWidth()*0.87f; }
        if(row==5){ return x > getWidth()*0.90f; }
        return false;
    }

    private void startSymbolRepeat(float x,float y){
        stopRepeat();
        repeatX=x; repeatY=y;
        repeat=()->{ handle(repeatX,repeatY); handler.postDelayed(repeat,110); };
        handler.postDelayed(repeat,600);
    }

    private boolean isBackspaceAt(float x,float y){
        return getRowAt(y)==2 && x>getWidth()*0.90f;
    }

    private void handle(float x,float y){
        float w=getWidth(), h=getHeight();
        int row=getRowAt(y);
        if(row<0||row>6) return;
        float nx=x/w, ny=y/h;

        if(row==0){
            // Normalized zones taken directly from the supplied reference image.
            float[] cuts={0.061f,0.122f,0.184f,0.279f,0.383f,0.463f,0.538f,0.614f,0.690f,0.770f,0.855f,0.916f,1.0f};
            int i=0; while(i<cuts.length && nx>cuts[i]) i++;
            if(i==0) service.move(KeyEvent.KEYCODE_DPAD_DOWN);
            else if(i==1) service.move(KeyEvent.KEYCODE_DPAD_UP);
            else if(i==2) service.voiceSearch(englishMode?"en-US":"fa-IR");
            else if(i==3) service.copyAll();
            else if(i==4) service.copyScreen();
            else if(i==5) service.paste();
            else if(i==6) service.cut();
            else if(i==7){service.undo();startUndoRepeat();}
            else if(i==8){service.redo();startRedoRepeat();}
            else if(i==9) showClipboardHistory();
            else if(i==10) showDrawer();
            else if(i==11) showMouseControls();
            else if(i==12){ hideTopRow=true; invalidate(); }
            return;
        }
        if(row==1){
            if(nx>0.73f && nx<0.88f){ hideSuggestionRow=true; invalidate(); return; }
            if(nx<0.17f && !suggestions[0].isEmpty()) service.replaceCurrentWord(suggestions[0]);
            else if(nx<0.31f && !suggestions[1].isEmpty()) service.replaceCurrentWord(suggestions[1]);
            else if(nx<0.47f && !suggestions[2].isEmpty()) service.replaceCurrentWord(suggestions[2]);
            return;
        }
        if(row==2){
            if(nx>0.91f){startBackspace();return;}
            int i=(int)(nx/0.077f);
            if(i<0)i=0; if(i>11)i=11;
            String[] normal=englishMode?new String[]{"1","2","3","4","5","6","7","8","9","0","-","="}:new String[]{"۱","۲","۳","۴","۵","۶","۷","۸","۹","۰","-","="};
            String[] shifted={"!","@","#","$","%","^","&","*","(",")","_","+"};
            service.type(caps?shifted[i]:normal[i]);
            return;
        }
        if(row==3){
            if(nx>0.91f){service.enter();return;}
            String[] keys=englishMode?new String[]{"q","w","e","r","t","y","u","i","o","p","[","]","\\"}:new String[]{"ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج","چ","پ"};
            int i=Math.min(12,(int)(nx/0.069f)); service.type(englishMode ? (caps?keys[i].toUpperCase(Locale.US):keys[i]) : keys[i]); return;
        }
        if(row==4){
            if(nx>0.91f){service.enter();return;}
            String[] keys=englishMode?new String[]{"Caps","a","s","d","f","g","h","j","k","l",";","'",""}:new String[]{"Caps","ظ","ط","ز","ر","ذ","ژ","د","ت","ن","م","ک","گ"};
            int i=Math.min(12,(int)(nx/0.069f));
            if(i==0){caps=!caps;invalidate();return;}
            if(englishMode && keys[i].isEmpty()) return;
            service.type(englishMode ? (caps?keys[i].toUpperCase(Locale.US):keys[i]) : keys[i]); return;
        }
        if(row==5){
            String[] keys=englishMode?new String[]{"z","x","c","v","b","n","m",",",".","/","?","",""}:new String[]{"ش","س","ی","ک","ب","ل","ا","ت","ن","م","و","ء","؟","،"};
            int i=Math.min(13,(int)(nx/0.071f));
            if(englishMode && keys[i].isEmpty()) return;
            service.type(englishMode ? (caps?keys[i].toUpperCase(Locale.US):keys[i]) : keys[i]); return;
        }
        if(row==6){
            if(nx<0.09f){showSymbolPicker();return;}
            if(nx<0.18f){englishMode=!englishMode;invalidate();return;}
            if(nx<0.27f){showEmojiPicker();return;}
            if(nx<0.57f){service.type(" ");return;}
            if(nx<0.66f){service.type(".");return;}
            if(nx<0.76f){service.moveCursorHorizontal(-1);return;}
            if(nx<0.86f){service.moveCursorHorizontal(1);return;}
            if(nx<0.93f){service.move(KeyEvent.KEYCODE_DPAD_UP);return;}
            service.move(KeyEvent.KEYCODE_DPAD_DOWN);
        }
    }

    private void showMouseControls(){
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,8,10,8);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "موس صفحه وب", headerClose);
        TextView info=new TextView(service);
        info.setText(KeyForgeAccessibilityService.isEnabled() ? "پد را بکشید تا نشانگر موس حرکت کند. برای کلیک، دکمه چپ یا راست را بزنید." : "برای کار واقعی موس روی صفحات وب، ابتدا دسترسی «KeyForge Mouse» را فعال کنید.");
        info.setTextSize(14); info.setGravity(Gravity.CENTER); root.addView(info,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout actions=new LinearLayout(service); actions.setGravity(Gravity.CENTER);
        Button enable=new Button(service); enable.setText("فعال‌سازی موس"); enable.setAllCaps(false);
        enable.setOnClickListener(v -> { try { service.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch(Exception ignored){} });
        actions.addView(enable,new LinearLayout.LayoutParams(0,dp(50),1f));
        root.addView(actions);
        final MousePadView pad=new MousePadView(service);
        root.addView(pad,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout clicks=new LinearLayout(service); clicks.setGravity(Gravity.CENTER);
        Button left=drawerButton("کلیک چپ"); left.setOnClickListener(v->KeyForgeAccessibilityService.click(false));
        Button right=drawerButton("کلیک راست"); right.setOnClickListener(v->KeyForgeAccessibilityService.click(true));
        clicks.addView(left,new LinearLayout.LayoutParams(0,dp(54),1f)); clicks.addView(right,new LinearLayout.LayoutParams(0,dp(54),1f));
        root.addView(clicks);
        Button stop=drawerButton("خاموش کردن موس");
        stop.setOnClickListener(v -> KeyForgeAccessibilityService.disable());
        root.addView(stop);
        final PopupWindow popup=new PopupWindow(root,Math.min(dp(390),Math.max(dp(310),getWidth()-dp(12))),Math.min(dp(600),Math.max(dp(420),getHeight()-dp(12))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private class MousePadView extends View {
        private final Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastX,lastY;
        MousePadView(Context c){super(c);setBackgroundColor(Color.rgb(245,247,250));}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c); float w=getWidth(),h=getHeight();
            mp.setStyle(Paint.Style.STROKE); mp.setStrokeWidth(dp(3)); mp.setColor(NAVY); c.drawRoundRect(dp(8),dp(8),w-dp(8),h-dp(8),dp(18),dp(18),mp);
            mp.setStyle(Paint.Style.FILL); mp.setColor(Color.LTGRAY); c.drawCircle(w/2f,h/2f,dp(20),mp);
            mp.setColor(NAVY); mp.setTextSize(dp(16)); mp.setTextAlign(Paint.Align.CENTER); c.drawText("حرکت نشانگر",w/2f,h/2f+dp(55),mp);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getX()-lastX,dy=e.getY()-lastY;lastX=e.getX();lastY=e.getY();KeyForgeAccessibilityService.movePointer(dx*1.8f,dy*1.8f);return true;}
            return true;
        }
    }

    private void showSymbolPicker(){
        final String[] symbols={"!","@","#","$","%","^","&","*","(",")","-","_","=","+","[","]","{","}","\\","|",";",":",",","<",".",">","/","؟","«","»","،","؛","٪","×","÷","±","≈","≠","≤","≥","∞","√","∑","π","µ","°","′","″","§","©","®","™","€","£","¥","₽","₹","$","¢","…","—","–","·","•","‰","※","†","‡","✓","✔","✕","✖","★","☆","♪","♫","♩","♥","♦","♣","♠","♂","♀","←","→","↑","↓","↔","↕","⇐","⇒","⇑","⇓","↗","↘","↙","↖","⌘","⌫","⌁","⌛","⚠","☑","☒","☀","☁","☂","☃","☄","☾","☽","♨","⚡","☕","☎","✉","✈","⚓","⚙","⚽","⚾","♟","♞","♜","♛","♚"};
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,10,10,10);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "نمادها", headerClose);
        ScrollView scroll=new ScrollView(service);
        GridLayout grid=new GridLayout(service); grid.setColumnCount(6); grid.setPadding(4,4,4,4);
        for(String s:symbols){
            Button b=new Button(service); b.setText(s); b.setTextSize(20); b.setAllCaps(false); b.setTextColor(Color.RED);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(58); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(2,2,2,2); grid.addView(b,lp);
            b.setOnClickListener(v -> service.typeUnit(s));
        }
        String[] extraSymbols={"⌁","⌂","⌃","⌄","⌘","⌥","⌃","⇧","⇪","↩","↪","⤴","⤵","↶","↷","⟳","⟲","⟶","⟵","⟷","⤒","⤓","⇤","⇥","⇠","⇢","⇡","⇣","↖","↗","↘","↙","↺","↻","⏎","␣","⌫","⌦","⎋","⏎","⏪","⏩","⏮","⏭","⏯","⏸","⏹","⏺","⏱","⏲","⏰","♩","♪","♫","♬","♭","♯","𝄞","∞","∝","∂","∇","∫","∬","∭","∮","∴","∵","∀","∃","∄","∅","∈","∉","⊂","⊃","⊆","⊇","∪","∩","∧","∨","¬","⊕","⊗","⊙","⊥","∥","∠","∟","△","▲","▼","◆","◇","■","□","●","○","◉","◎","◌","◍","◐","◑","◒","◓","☑","☒","☐","✓","✔","✗","✘","✦","✧","✩","✪","✫","✬","✭","✮","✯","✰","☮","☯","☪","✡","☸","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","♀","♂","⚕","⚖","⚗","⚔","⚑","⚐","⚜","♻","☢","☣","⚠","⛔","🚫","🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤","🔶","🔷","🔺","🔻","◀","▶","⏫","⏬","⬅","➡","⬆","⬇","↔","↕","↯","⇐","⇒","⇑","⇓"};
        for(String s:extraSymbols){ Button b=new Button(service); b.setText(s); b.setTextSize(20); b.setAllCaps(false); b.setTextColor(Color.RED); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(58); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(2,2,2,2); grid.addView(b,lp); b.setOnClickListener(v->service.typeUnit(s)); setSymbolButtonRepeat(b,s); }
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2)); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        String[] moreSymbols={"⌖","⌗","⌑","⌘","⌥","⇥","⇤","↹","␍","␊","␉","␠","⌦","⌫","⎋","⏎","⌧","⌨","⏏","⏮","⏪","⏩","⏭","⏯","⏸","⏹","⏺","⏱","⏲","⏰","⏳","∎","□","■","▢","▣","▤","▥","▦","▧","▨","▩","▪","▫","▬","▭","▲","△","▼","▽","◆","◇","◈","◉","○","●","◌","◍","◐","◑","◒","◓","◔","◕","◖","◗","◠","◡","◢","◣","◤","◥","※","⁂","⁑","⁕","⁖","⁘","⁙","⁜","⁝","⁞","‖","¦","‗","¯","ˉ","ˊ","ˋ","˙","¨","ˆ","˜","˚","¸","˛","˝","ˇ","¡","¿","‹","›","„","“","”","‘","’","‚","«","»","⟨","⟩","⟪","⟫","⟦","⟧","⟮","⟯","⦃","⦄","∈","∉","∋","∌","⊂","⊃","⊄","⊅","⊆","⊇","⊈","⊉","∪","∩","⊎","⊓","⊔","∧","∨","⊻","¬","⊢","⊣","⊨","⊭","⊤","⊥","∥","∦","∝","∼","≃","≅","≡","≢","≈","≉","≠","≮","≯","≤","≥","≪","≫","∓","∔","∕","∗","∘","∙","∶","∷","∴","∵","∽","∾","∿","∫","∬","∭","∮","∯","∰","∇","∆","∂","ℏ","ℓ","℘","ℜ","ℑ","ℵ","ℕ","ℤ","ℚ","ℝ","ℂ","°","′","″","‴","‰","‱","№","℗","℠","™","©","®","℮","₿","₽","₺","₴","₩","₦","₫","₡","₲","₵","₸","₹","€","£","¥","¢","¤","₱","₪","﷼","٪","٫","٬","ـ","‍","‌","﻿","​","…","⋯","⋮","⋰","⋱","—","–","‑","‒","―","_","-","+","=","*","/","\\","|","~","`","^","%","&","@","#","$"};
for(String s:moreSymbols){ Button b=new Button(service); b.setText(s); b.setTextSize(20); b.setAllCaps(false); b.setTextColor(Color.RED); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(56); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(2,2,2,2); grid.addView(b,lp); b.setOnClickListener(v->service.typeUnit(s)); setSymbolButtonRepeat(b,s); }
        final PopupWindow popup=new PopupWindow(root,Math.min(dp(360),Math.max(dp(300),getWidth()-dp(16))),Math.min(dp(620),Math.max(dp(360),getHeight()-dp(16))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss());
        popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private void setSymbolButtonRepeat(Button b, String symbol){
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                service.typeUnit(symbol);
                stopRepeat();
                repeat=()->{ service.typeUnit(symbol); handler.postDelayed(repeat,110); };
                handler.postDelayed(repeat,600);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){
                stopRepeat();
                return true;
            }
            return true;
        });
    }

    private void startBackspace(){
        service.backspace();
        stopRepeat();
        repeat=()->{ service.backspace(); handler.postDelayed(repeat,55); };
        handler.postDelayed(repeat,600);
    }

    private void startUndoRepeat(){
        stopRepeat();
        repeat=()->{ service.undo(); handler.postDelayed(repeat,80); };
        handler.postDelayed(repeat,350);
    }

    private void startRedoRepeat(){
        stopRepeat();
        repeat=()->{ service.redo(); handler.postDelayed(repeat,80); };
        handler.postDelayed(repeat,350);
    }

    private void stopRepeat(){
        if(repeat!=null){
            handler.removeCallbacks(repeat);
            repeat=null;
        }
    }
}
