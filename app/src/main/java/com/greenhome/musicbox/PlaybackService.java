package com.greenhome.musicbox;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
/**
 * 后台播放服务：屏幕关掉、回到微信后音乐不中断。
 * ExoPlayer 挂在服务上，锁屏通知栏也能控制播放。
 */
public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        AudioAttributes music = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        ExoPlayer player = new ExoPlayer.Builder(this)
                .setAudioAttributes(music, /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)   // 拔耳机自动暂停
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        player.setRepeatMode(ExoPlayer.REPEAT_MODE_OFF);
        if (BuildConfig.DEBUG) {
            player.addAnalyticsListener(new EventLogger("MusicBoxDebug"));
        }
        mediaSession = new MediaSession.Builder(this, player).build();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.getPlayer().release();
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}
