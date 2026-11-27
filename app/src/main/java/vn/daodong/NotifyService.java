package vn.daodong;

import android.app.Notification;
import android.content.ComponentName;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.List;

public class NotifyService extends NotificationListenerService {
    static NotifyService instance;
    private MediaSessionManager sessions;
    private MediaController current;
    private final java.util.Map<MediaController, MediaController.Callback> watched = new java.util.HashMap<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener onSessions = this::watch;

    @Override public void onListenerConnected() {
        instance = this;
        sessions = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        ComponentName me = new ComponentName(this, NotifyService.class);
        sessions.addOnActiveSessionsChangedListener(onSessions, me);
        watch(sessions.getActiveSessions(me));
    }

    @Override public void onListenerDisconnected() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(onSessions);
        unwatch();
        instance = null;
        current = null;
        publish();
    }

    private void unwatch() {
        for (java.util.Map.Entry<MediaController, MediaController.Callback> e : watched.entrySet())
            e.getKey().unregisterCallback(e.getValue());
        watched.clear();
    }

    private void watch(List<MediaController> list) {
        unwatch();
        if (list != null) for (MediaController c : list) {
            MediaController.Callback cb = new MediaController.Callback() {
                @Override public void onPlaybackStateChanged(PlaybackState s) { pick(); }
                @Override public void onMetadataChanged(android.media.MediaMetadata m) { pick(); }
            };
            c.registerCallback(cb);
            watched.put(c, cb);
        }
        pick();
    }

    /** Ưu tiên trình phát đang chạy; nếu tạm dừng thì giữ trình phát gần nhất. */
    private void pick() {
        MediaController playing = null;
        for (MediaController c : watched.keySet()) if (isPlaying(c)) playing = c;
        if (playing != null) current = playing;
        else if (!watched.containsKey(current)) current = null;
        publish();
    }

    static boolean isPlaying(MediaController c) {
        PlaybackState s = c == null ? null : c.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    MediaController media() { return current; }

    private void publish() {
        if (IslandService.instance != null) IslandService.instance.onMedia(current);
    }

    @Override public void onNotificationPosted(StatusBarNotification n) {
        IslandService island = IslandService.instance;
        if (island == null || n.isOngoing() || n.getPackageName().equals(getPackageName())) return;
        Notification x = n.getNotification();
        if ((x.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if (x.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return;
        // Bỏ qua thông báo im lặng
        Ranking r = new Ranking();
        if (getCurrentRanking().getRanking(n.getKey(), r)
                && (r.getImportance() < android.app.NotificationManager.IMPORTANCE_DEFAULT || !r.matchesInterruptionFilter())) return;
        CharSequence title = x.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = x.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && text == null) return;
        island.onNotification(n.getPackageName(), title == null ? "" : title.toString(),
                text == null ? "" : text.toString(), x.contentIntent);
    }
}
