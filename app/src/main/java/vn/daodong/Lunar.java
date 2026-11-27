package vn.daodong;

/** Đổi dương lịch sang âm lịch Việt Nam (thuật toán Hồ Ngọc Đức, múi giờ +7). */
public final class Lunar {
    private static final double TZ = 7.0;

    private static int jdn(int d, int m, int y) {
        int a = (14 - m) / 12, yy = y + 4800 - a, mm = m + 12 * a - 3;
        return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
    }

    private static int newMoon(int k) {
        double T = k / 1236.85, T2 = T * T, T3 = T2 * T, dr = Math.PI / 180;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3
                + 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
        double M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
        double Mp = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
        double F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
        double c = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M)
                - 0.4068 * Math.sin(Mp * dr) + 0.0161 * Math.sin(dr * 2 * Mp) - 0.0004 * Math.sin(dr * 3 * Mp)
                + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mp))
                - 0.0074 * Math.sin(dr * (M - Mp)) + 0.0004 * Math.sin(dr * (2 * F + M))
                - 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mp))
                + 0.0010 * Math.sin(dr * (2 * F - Mp)) + 0.0005 * Math.sin(dr * (2 * Mp + M));
        double dt = T < -11 ? 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3
                : -0.000278 + 0.000265 * T + 0.000262 * T2;
        return (int) Math.floor(jd1 + c - dt + 0.5 + TZ / 24);
    }

    private static int sunSector(int day) {
        double T = (day - 0.5 - TZ / 24 - 2451545.0) / 36525, T2 = T * T, dr = Math.PI / 180;
        double M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
        double L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
        double DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M)
                + (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M);
        double L = (L0 + DL) * dr;
        L = L - Math.PI * 2 * Math.floor(L / (Math.PI * 2));
        return (int) Math.floor(L / Math.PI * 6);
    }

    private static int month11(int y) {
        int k = (int) Math.floor((jdn(31, 12, y) - 2415021) / 29.530588853);
        int nm = newMoon(k);
        return sunSector(nm) >= 9 ? newMoon(k - 1) : nm;
    }

    private static int leapOffset(int a11) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int i = 1, arc = sunSector(newMoon(k + i)), last;
        do { last = arc; i++; arc = sunSector(newMoon(k + i)); } while (arc != last && i < 14);
        return i - 1;
    }

    /** Trả về {ngày, tháng, năm, nhuận(0/1)}. */
    public static int[] fromSolar(int d, int m, int y) {
        int day = jdn(d, m, y);
        int k = (int) Math.floor((day - 2415021.076998695) / 29.530588853);
        int start = newMoon(k + 1);
        if (start > day) start = newMoon(k);
        int a11 = month11(y), b11 = a11, ly;
        if (a11 >= start) { ly = y; a11 = month11(y - 1); }
        else { ly = y + 1; b11 = month11(y + 1); }
        int diff = (int) Math.floor((start - a11) / 29.0);
        int leap = 0, lm = diff + 11;
        if (b11 - a11 > 365) {
            int off = leapOffset(a11);
            if (diff >= off) { lm = diff + 10; if (diff == off) leap = 1; }
        }
        if (lm > 12) lm -= 12;
        if (lm >= 11 && diff < 4) ly -= 1;
        return new int[]{day - start + 1, lm, ly, leap};
    }

    public static String shortText(java.util.Calendar c) {
        int[] l = fromSolar(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.YEAR));
        return "ÂL " + l[0] + "/" + l[1] + (l[3] == 1 ? " nhuận" : "");
    }

    public static String yearName(int y) {
        String[] can = {"Canh", "Tân", "Nhâm", "Quý", "Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ"};
        String[] chi = {"Thân", "Dậu", "Tuất", "Hợi", "Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi"};
        return can[y % 10] + " " + chi[y % 12];
    }
}
