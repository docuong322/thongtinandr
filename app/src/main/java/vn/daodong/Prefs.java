package vn.daodong;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static SharedPreferences of(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }
}
