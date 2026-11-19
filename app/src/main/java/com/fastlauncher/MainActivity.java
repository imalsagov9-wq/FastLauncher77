package com.fastlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF8BDCFF;
    private static final int BG_TOP = 0xFF1C2538;
    private static final int BG_BOTTOM = 0xFF0C111C;
    private static final int CARD = 0xFF232E42;
    private static final int CARD_FOCUS = 0xFF33425E;

    static class App {
        String label;
        String pkg;
        ComponentName cn;
        Drawable icon;
    }

    private HorizontalScrollView scroll;
    private LinearLayout row;
    private float density;
    private int loadToken = 0;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            load();
        }
    };

    private int dp(int v) {
        return Math.round(v * density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(36), 0, 0);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM}));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(56);
        root.addView(time, tlp);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(0xFFA3AEC4);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = dp(56);
        root.addView(date, dlp);

        scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(56), dp(30), dp(40), dp(30));

        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        scroll.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        load();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (row != null && row.getChildCount() > 0) {
            scroll.smoothScrollTo(0, 0);
            row.getChildAt(0).requestFocus();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && row != null && row.getChildCount() > 0) {
            int k = e.getKeyCode();
            boolean vertical = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
            boolean horizontal = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (vertical || horizontal) {
                if (row.getFocusedChild() == null) {
                    row.getChildAt(0).requestFocus();
                    return true;
                }
                if (vertical) return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_SCROLL && scroll != null) {
            float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (v != 0f) {
                scroll.smoothScrollBy((int) (-v * dp(180)), 0);
                return true;
            }
        }
        return super.onGenericMotionEvent(e);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Launcher: Back does nothing.
    }

    @Override
    protected void onDestroy() {
        loadToken++;
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private void load() {
        final int token = ++loadToken;
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Map<String, App> map = new LinkedHashMap<>();
            collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, map);
            collect(pm, Intent.CATEGORY_LAUNCHER, map);
            final List<App> list = new ArrayList<>(map.values());
            Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
            runOnUiThread(() -> {
                if (token != loadToken || isDestroyed()) return;
                populate(list);
            });
        }).start();
    }

    private void populate(List<App> list) {
        row.removeAllViews();
        for (App a : list) {
            row.addView(makeCard(a));
        }
        row.postDelayed(() -> {
            if (row.getChildCount() > 0 && row.getFocusedChild() == null) {
                row.getChildAt(0).requestFocus();
            }
        }, 150);
    }

    private void collect(PackageManager pm, String category, Map<String, App> map) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            ActivityInfo ai = r.activityInfo;
            if (ai == null) continue;
            if (ai.packageName.equals(getPackageName())) continue;
            if (map.containsKey(ai.packageName)) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.cn = new ComponentName(ai.packageName, ai.name);
            a.label = String.valueOf(r.loadLabel(pm));
            a.icon = shrink(r.loadIcon(pm), dp(72));
            map.put(a.pkg, a);
        }
    }

    private Drawable shrink(Drawable d, int px) {
        try {
            Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, px, px);
            d.draw(c);
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private GradientDrawable shape(int color, int strokePx, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(18));
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    private View makeCard(final App a) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(8), dp(12), dp(8), dp(10));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(150), dp(150));
        clp.rightMargin = dp(16);
        c.setLayoutParams(clp);

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, shape(CARD_FOCUS, dp(3), ACCENT));
        bg.addState(new int[]{}, shape(CARD, 0, 0));
        c.setBackground(bg);

        c.setFocusable(true);
        c.setFocusableInTouchMode(true);
        c.setClickable(true);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(a.icon);
        c.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView label = new TextView(this);
        label.setText(a.label);
        label.setTextColor(0xFFEEF2FA);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        c.addView(label, lp);

        c.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(110).start();
            v.setTranslationZ(has ? dp(8) : 0);
        });

        // Mouse hover focuses a card only if it is fully visible (prevents runaway scrolling).
        c.setOnHoverListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_HOVER_ENTER && !v.hasFocus()) {
                Rect r = new Rect();
                if (v.getGlobalVisibleRect(r) && r.width() >= v.getWidth() * 0.95f) {
                    v.requestFocus();
                }
            }
            return false;
        });

        c.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(a.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть", Toast.LENGTH_SHORT).show();
            }
        });

        // Long press / long OK: app info (disable, clear cache, uninstall)
        c.setOnLongClickListener(v -> {
            Intent s = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.pkg));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(s);
            } catch (Exception ignored) {
            }
            return true;
        });

        return c;
    }
}
