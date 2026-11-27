package vn.daodong;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.Calendar;

public class MainActivity extends Activity {
    private LinearLayout box;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad * 2, pad, pad * 2);
        scroll.addView(box);
        setContentView(scroll);

        Calendar c = Calendar.getInstance();
        int[] l = Lunar.fromSolar(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
        label("Đảo Động", 26);
        label("Hôm nay âm lịch: " + l[0] + "/" + l[1] + (l[3] == 1 ? " nhuận" : "") + " năm " + Lunar.yearName(l[2]), 16);

        label("Bước 1: cấp quyền", 18);
        button("Bật Trợ năng (vẽ đảo + âm lịch)", Settings.ACTION_ACCESSIBILITY_SETTINGS);
        button("Bật quyền đọc thông báo (thông báo + nhạc)", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        label("Nếu công tắc bị mờ: Cài đặt > Ứng dụng > Đảo Động > menu ⋮ > Cho phép cài đặt bị hạn chế.", 13);

        label("Tính năng", 18);
        toggle("Hiện đảo", "island");
        toggle("Hiện thông báo trên đảo", "notify");
        toggle("Hiện nhạc đang phát", "music");
        toggle("Hiện khi cắm sạc", "charge");
        toggle("Hiện âm lịch cạnh ngày", "lunar");

        label("Chỉnh đảo cho khớp tai thỏ / camera", 18);
        slider("Chiều rộng", "w", 120, 60, 240);
        slider("Chiều cao", "h", 30, 20, 60);
        slider("Lệch trái/phải (100 = giữa)", "x", 100, 0, 200);
        slider("Cách mép trên", "y", 4, 0, 60);

        label("Chỉnh vị trí chữ âm lịch", 18);
        slider("Sang trái/phải (100 = mặc định)", "lx", 100, 0, 300);
        slider("Lên/xuống (50 = mặc định)", "ly", 50, 0, 100);
    }

    private void label(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, sp > 16 ? 36 : 8, 0, 8);
        box.addView(t);
    }

    private void button(String s, String action) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(v -> startActivity(new Intent(action)));
        box.addView(b);
    }

    private void toggle(String s, String key) {
        SharedPreferences p = Prefs.of(this);
        Switch sw = new Switch(this);
        sw.setText(s);
        sw.setChecked(p.getBoolean(key, true));
        sw.setPadding(0, 16, 0, 16);
        sw.setOnCheckedChangeListener((v, on) -> p.edit().putBoolean(key, on).apply());
        box.addView(sw);
    }

    private void slider(String name, String key, int def, int min, int max) {
        SharedPreferences p = Prefs.of(this);
        TextView t = new TextView(this);
        t.setText(name + ": " + p.getInt(key, def));
        t.setPadding(0, 16, 0, 0);
        SeekBar s = new SeekBar(this);
        s.setMin(min);
        s.setMax(max);
        s.setProgress(p.getInt(key, def));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int v, boolean user) {
                t.setText(name + ": " + v);
                if (user) p.edit().putInt(key, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        box.addView(t);
        box.addView(s);
    }
}
