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
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
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

    private static final int ACCENT = 0xFF5EC8FF;
    private static final int BG_TOP = 0xFF0E1320;
    private static final int BG_BOTTOM = 0xFF05070C;
    private static final int CARD = 0xFF141A26;
    private static final int CARD_FOCUS = 0xFF222C3F;

    static class App {
        String label;
        String pkg;
        ComponentName cn;
        Drawable icon;
    }

    static class Holder {
        ImageView icon;
        TextView label;
        App app;
    }

    private final List<App> apps = new ArrayList<>();
    private GridView grid;
    private AppAdapter adapter;
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
        root.setPadding(dp(56), dp(36), dp(56), 0);
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
        root.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(0xFF8A94A8);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        root.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(170));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(16));
        grid.setVerticalSpacing(dp(16));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(dp(12), dp(28), dp(12), dp(28));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        root.addView(grid, new LinearLayout.LayoutParams(
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
        if (grid != null && adapter.getCount() > 0) {
            grid.setSelection(0);
            View c = grid.getChildAt(0);
            if (c != null) c.requestFocus();
        }
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
                boolean first = apps.isEmpty();
                apps.clear();
                apps.addAll(list);
                adapter.notifyDataSetChanged();
                if (first) {
                    grid.postDelayed(() -> {
                        View c = grid.getChildAt(0);
                        if (c != null && grid.getFocusedChild() == null) c.requestFocus();
                    }, 150);
                }
            });
        }).start();
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
            a.icon = shrink(r.loadIcon(pm), dp(64));
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
        g.setCornerRadius(dp(16));
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    private View makeCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(136)));
        c.setPadding(dp(8), dp(12), dp(8), dp(10));

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, shape(CARD_FOCUS, dp(3), ACCENT));
        bg.addState(new int[]{}, shape(CARD, 0, 0));
        c.setBackground(bg);

        c.setFocusable(true);
        c.setFocusableInTouchMode(true);
        c.setClickable(true);

        final Holder h = new Holder();
        h.icon = new ImageView(this);
        h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        c.addView(h.icon, new LinearLayout.LayoutParams(dp(64), dp(64)));

        h.label = new TextView(this);
        h.label.setTextColor(0xFFE6EAF2);
        h.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        h.label.setSingleLine(true);
        h.label.setEllipsize(TextUtils.TruncateAt.END);
        h.label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        c.addView(h.label, lp);

        c.setTag(h);

        c.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(110).start();
            v.setTranslationZ(has ? dp(8) : 0);
        });

        c.setOnHoverListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_HOVER_ENTER && !v.hasFocus()) {
                v.requestFocus();
            }
            return false;
        });

        c.setOnClickListener(v -> {
            if (h.app == null) return;
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(h.app.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть", Toast.LENGTH_SHORT).show();
            }
        });

        // Long press / long OK: open app info (disable, clear cache, uninstall)
        c.setOnLongClickListener(v -> {
            if (h.app == null) return true;
            Intent s = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + h.app.pkg));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(s);
            } catch (Exception ignored) {
            }
            return true;
        });

        return c;
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int i) {
            return apps.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = makeCard();
            Holder h = (Holder) v.getTag();
            App a = apps.get(i);
            h.app = a;
            h.icon.setImageDrawable(a.icon);
            h.label.setText(a.label);
            return v;
        }
    }
}
