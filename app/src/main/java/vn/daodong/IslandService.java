package vn.daodong;

import android.accessibilityservice.AccessibilityService;
import android.app.PendingIntent;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.*;
import java.util.regex.Pattern;

public class IslandService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    static IslandService instance;

    private static final int IDLE = 0, MUSIC = 1, MUSIC_BIG = 2, NOTE = 3, CHARGE = 4;
    /** Dòng ngày của hệ thống, ví dụ "Th 6, 27 thg 11", "Thứ Sáu, 27 tháng 11", "Fri, Nov 27". */
    private static final Pattern DATE = Pattern.compile(
            "(?i)^(th ?\\d|th |thứ |cn|chủ nhật|mon|tue|wed|thu|fri|sat|sun).{0,25}\\d.{0,15}$");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private LinearLayout island;
    private WindowManager.LayoutParams islandLp;
    private TextView lunar;
    private WindowManager.LayoutParams lunarLp;

    private int state = IDLE;
    private MediaController media;
    private String notePkg, noteTitle, noteText;
    private PendingIntent noteIntent;
    private int battery;
    private boolean landscape;

    private final Runnable collapse = () -> { state = restingState(); render(); };
    private final Runnable findDate = this::updateLunar;

    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!Prefs.of(IslandService.this).getBoolean("charge", true)) return;
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return;
            battery = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
            show(CHARGE, 3000);
        }
    };

    @Override protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        island = new LinearLayout(this);
        island.setOrientation(LinearLayout.VERTICAL);
        island.setGravity(Gravity.CENTER);
        island.setOnClickListener(v -> onTap());
        island.setOnLongClickListener(v -> { if (media != null) show(MUSIC_BIG, 6000); return true; });
        islandLp = overlayParams();
        islandLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        wm.addView(island, islandLp);

        lunar = new TextView(this);
        lunar.setTextColor(Color.WHITE);
        lunar.setTextSize(14);
        lunar.setPadding(dp(8), dp(2), dp(8), dp(2));
        lunar.setBackground(round(0xCC8B1E1E, dp(10)));
        lunar.setVisibility(View.GONE);
        lunarLp = overlayParams();
        lunarLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        lunarLp.gravity = Gravity.TOP | Gravity.LEFT;
        wm.addView(lunar, lunarLp);

        IntentFilter f = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        registerReceiver(power, f);
        Prefs.of(this).registerOnSharedPreferenceChangeListener(this);

        if (NotifyService.instance != null) media = NotifyService.instance.media();
        state = restingState();
        render();
    }

    @Override public boolean onUnbind(Intent intent) {
        if (instance == this) {
            instance = null;
            ui.removeCallbacksAndMessages(null);
            unregisterReceiver(power);
            Prefs.of(this).unregisterOnSharedPreferenceChangeListener(this);
            wm.removeView(island);
            wm.removeView(lunar);
        }
        return false;
    }

    private WindowManager.LayoutParams overlayParams() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28)
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }

    // ---------- Sự kiện đầu vào ----------

    void onMedia(MediaController c) {
        ui.post(() -> {
            media = Prefs.of(this).getBoolean("music", true) ? c : null;
            if (state == NOTE || state == CHARGE) return;
            if (state == MUSIC_BIG && media != null) render();
            else { state = restingState(); render(); }
        });
    }

    void onNotification(String pkg, String title, String text, PendingIntent intent) {
        ui.post(() -> {
            if (!Prefs.of(this).getBoolean("notify", true)) return;
            notePkg = pkg; noteTitle = title; noteText = text; noteIntent = intent;
            // Đang khóa máy thì không để lộ nội dung
            if (((android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()) {
                noteTitle = "Thông báo mới"; noteText = "Mở khóa để xem"; noteIntent = null;
            }
            show(NOTE, 4500);
        });
    }

    private int restingState() { return NotifyService.isPlaying(media) ? MUSIC : IDLE; }

    private void show(int s, int millis) {
        ui.removeCallbacks(collapse);
        state = s;
        render();
        ui.postDelayed(collapse, millis);
    }

    private void onTap() {
        if (state == NOTE && noteIntent != null) {
            try { noteIntent.send(); } catch (PendingIntent.CanceledException ignored) { }
            collapse.run();
        } else if (state == MUSIC_BIG) {
            collapse.run();
        } else if (media != null) {
            show(MUSIC_BIG, 6000);
        } else {
            java.util.Calendar c = java.util.Calendar.getInstance();
            int[] l = Lunar.fromSolar(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.YEAR));
            notePkg = getPackageName();
            noteTitle = "Âm lịch: ngày " + l[0] + " tháng " + l[1] + (l[3] == 1 ? " nhuận" : "");
            noteText = "Năm " + Lunar.yearName(l[2]);
            noteIntent = null;
            show(NOTE, 3000);
        }
    }

    // ---------- Vẽ đảo ----------

    private void render() {
        if (island == null || instance != this) return;
        SharedPreferences p = Prefs.of(this);
        int w = dp(p.getInt("w", 120)), h = dp(p.getInt("h", 30));
        int full = Math.min(getResources().getDisplayMetrics().widthPixels - dp(16), dp(380));
        island.removeAllViews();
        int width = w, height = h, radius = h / 2;

        if (state == MUSIC) {
            width = w + dp(60);
            LinearLayout row = row();
            row.addView(art(h - dp(8)));
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            row.addView(text("♫ ▮▮▯", 12, 0xFF4ADE80));
            island.addView(row, new LinearLayout.LayoutParams(-1, -1));
        } else if (state == CHARGE) {
            width = w + dp(110);
            LinearLayout row = row();
            row.addView(text("⚡ Đang sạc", 13, Color.WHITE));
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            row.addView(text(battery + "%", 13, 0xFF4ADE80));
            island.addView(row, new LinearLayout.LayoutParams(-1, -1));
        } else if (state == NOTE) {
            width = full; height = h + dp(64); radius = dp(28);
            LinearLayout row = row();
            ImageView icon = new ImageView(this);
            try { Drawable d = getPackageManager().getApplicationIcon(notePkg); icon.setImageDrawable(d); }
            catch (Exception e) { icon.setImageResource(android.R.drawable.ic_dialog_info); }
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            row.addView(twoLines(noteTitle, noteText));
            island.addView(row, new LinearLayout.LayoutParams(-1, -1));
        } else if (state == MUSIC_BIG && media != null) {
            width = full; height = LinearLayout.LayoutParams.WRAP_CONTENT; radius = dp(32);
            MediaMetadata m = media.getMetadata();
            String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            LinearLayout top = row();
            top.addView(art(dp(52)));
            top.addView(twoLines(title == null ? "Đang phát" : title, artist == null ? "" : artist));
            top.setPadding(dp(16), dp(14), dp(16), 0);
            island.addView(top, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout controls = row();
            controls.setGravity(Gravity.CENTER);
            controls.addView(button(android.R.drawable.ic_media_previous, () -> media.getTransportControls().skipToPrevious()));
            boolean playing = NotifyService.isPlaying(media);
            controls.addView(button(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, () -> {
                if (NotifyService.isPlaying(media)) media.getTransportControls().pause();
                else media.getTransportControls().play();
            }));
            controls.addView(button(android.R.drawable.ic_media_next, () -> media.getTransportControls().skipToNext()));
            island.addView(controls, new LinearLayout.LayoutParams(-1, dp(56)));
        }

        island.setBackground(round(Color.BLACK, radius));
        islandLp.width = width;
        islandLp.height = height;
        islandLp.x = dp(p.getInt("x", 100) - 100);
        islandLp.y = dp(p.getInt("y", 4));
        island.setVisibility(landscape || !p.getBoolean("island", true) ? View.GONE : View.VISIBLE);
        wm.updateViewLayout(island, islandLp);
        island.setAlpha(0.6f);
        island.animate().alpha(1f).setDuration(180).start();
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), 0, dp(12), 0);
        return r;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private View twoLines(String a, String b) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(text(a, 15, Color.WHITE));
        col.addView(text(b, 13, 0xFFBBBBBB));
        col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        return col;
    }

    private View art(int size) {
        ImageView v = new ImageView(this);
        MediaMetadata m = media == null ? null : media.getMetadata();
        Bitmap b = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null && m != null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b != null) v.setImageBitmap(b); else v.setImageResource(android.R.drawable.ic_media_play);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setBackground(round(0xFF333333, size / 4));
        v.setClipToOutline(true);
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return v;
    }

    private View button(int icon, Runnable action) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setColorFilter(Color.WHITE);
        b.setOnClickListener(v -> { if (media != null) { action.run(); show(MUSIC_BIG, 6000); } });
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(72), -1));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------- Âm lịch cạnh dòng ngày ----------

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence pkg = e.getPackageName();
        boolean system = pkg != null && pkg.toString().contains("systemui");
        if (system || e.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ui.removeCallbacks(findDate);
            ui.postDelayed(findDate, 150);
        }
    }

    private void updateLunar() {
        if (instance != this) return;
        SharedPreferences p = Prefs.of(this);
        Rect found = null;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (p.getBoolean("lunar", true) && pm.isInteractive()) {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || root.getPackageName() == null || !root.getPackageName().toString().contains("systemui")) continue;
                found = findDateNode(root, 0);
                if (found != null) break;
            }
        }
        if (found == null) { lunar.setVisibility(View.GONE); return; }
        lunar.setText(Lunar.shortText(java.util.Calendar.getInstance()));
        lunar.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        lunarLp.x = found.right + dp(p.getInt("lx", 100) - 92);
        lunarLp.y = found.centerY() - lunar.getMeasuredHeight() / 2 + dp(p.getInt("ly", 50) - 50);
        lunar.setVisibility(View.VISIBLE);
        wm.updateViewLayout(lunar, lunarLp);
    }

    private Rect findDateNode(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 30) return null;
        CharSequence t = n.getText();
        if (t != null && n.isVisibleToUser() && DATE.matcher(t.toString().trim()).matches()) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            // Chỉ nhận dòng ngày nằm ở nửa trên màn hình
            if (r.width() > 0 && r.top < getResources().getDisplayMetrics().heightPixels / 2) return r;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            Rect r = findDateNode(n.getChild(i), depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        media = p.getBoolean("music", true) && NotifyService.instance != null ? NotifyService.instance.media() : null;
        state = restingState();
        render();
        updateLunar();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        landscape = c.orientation == Configuration.ORIENTATION_LANDSCAPE;
        render();
    }

    @Override public void onInterrupt() { }
}
