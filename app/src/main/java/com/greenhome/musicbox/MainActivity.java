package com.greenhome.musicbox;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 主界面：为老人设计的大字大按钮播放器。
 * 功能：播放列表 / 上一首·下一首 / 播放·暂停 / 半音升降调 / 从微信导入歌曲。
 */
public class MainActivity extends AppCompatActivity {

    private PlaylistStore store;
    private SongAdapter adapter;
    private List<Song> songs = new ArrayList<>();
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;

    private TextView currentTitle;
    private TextView keyLabel;
    private TextView timeNow;
    private TextView timeTotal;
    private SeekBar seekBar;
    private Button btnPlayPause;
    private ListView listSongs;

    private int semitones = 0;
    private boolean userSeeking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgressUi();
            handler.postDelayed(this, 500);
        }
    };

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    int added = importUri(uri);
                    if (added == 0) {
                        toast("这个文件不是音乐，换一个试试");
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = new PlaylistStore(this);
        semitones = store.loadSemitones();
        songs = store.load();

        bindViews();
        setupListView();
        setupButtons();

        adapter.setSongs(songs);
        refreshEmptyState();

        requestNotificationPermissionIfNeeded();
        connectController();

        handleImportIntent(getIntent());

        if (!store.loadHelpShown()) {
            showHelpDialog(true);
            store.saveHelpShown();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleImportIntent(intent);
    }

    // ---------- 视图 ----------

    private void bindViews() {
        currentTitle = findViewById(R.id.currentTitle);
        keyLabel = findViewById(R.id.keyLabel);
        timeNow = findViewById(R.id.timeNow);
        timeTotal = findViewById(R.id.timeTotal);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        listSongs = findViewById(R.id.listSongs);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) timeNow.setText(formatTime(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                if (controller != null) {
                    controller.seekTo(sb.getProgress());
                }
            }
        });
    }

    private void setupListView() {
        adapter = new SongAdapter(this, this::confirmDeleteSong);
        listSongs.setAdapter(adapter);
        listSongs.setOnItemClickListener((parent, view, position, id) -> playSongAt(position));
    }

    private void setupButtons() {
        findViewById(R.id.btnPitchDown).setOnClickListener(v -> changeSemitones(-1));
        findViewById(R.id.btnPitchUp).setOnClickListener(v -> changeSemitones(1));
        findViewById(R.id.btnResetPitch).setOnClickListener(v -> changeSemitones(0));

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        findViewById(R.id.btnPrev).setOnClickListener(v -> playPrevious());
        findViewById(R.id.btnNext).setOnClickListener(v -> playNext());
        findViewById(R.id.btnAdd).setOnClickListener(v -> filePicker.launch(new String[]{"audio/*"}));
        findViewById(R.id.btnHelp).setOnClickListener(v -> showHelpDialog(false));

        updateKeyLabel();
    }

    // ---------- 连接后台播放服务 ----------

    private void connectController() {
        SessionToken token = new SessionToken(this, new android.content.ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                MediaController c = controllerFuture.get();
                runOnUiThread(() -> onControllerReady(c));
            } catch (Exception e) {
                runOnUiThread(() -> toast("播放器启动失败，请重新打开应用"));
            }
        }, Runnable::run);
    }

    private void onControllerReady(MediaController c) {
        controller = c;
        controller.addListener(playerListener);

        if (!songs.isEmpty()) {
            int index = Math.max(0, Math.min(songs.size() - 1, store.loadLastIndex()));
            controller.setMediaItems(buildMediaItems(), index, store.loadLastPositionMs());
            applyPitch(semitones, false);
            adapter.setCurrentPlaying(index);
            if (store.loadWasPlaying()) {
                controller.prepare();
                controller.play();
            } else {
                updateTitleFromPlayer();
            }
        }
        updatePlayPauseButton();
        updateProgressUi();
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            updateTitleFromPlayer();
            if (controller != null) {
                adapter.setCurrentPlaying(controller.getCurrentMediaItemIndex());
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updatePlayPauseButton();
            if (isPlaying) {
                handler.post(ticker);
            } else {
                handler.removeCallbacks(ticker);
                updateProgressUi();
            }
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            updatePlayPauseButton();
            updateProgressUi();
        }
    };

    // ---------- 播放控制 ----------

    private void playSongAt(int position) {
        if (controller == null || songs.isEmpty()) return;
        controller.seekTo(position, 0);
        controller.prepare();
        controller.play();
    }

    private void togglePlayPause() {
        if (controller == null) return;
        if (songs.isEmpty()) {
            toast("列表里还没有歌，先点绿色「＋ 添加歌曲」");
            return;
        }
        int state = controller.getPlaybackState();
        if (state == Player.STATE_IDLE) {
            controller.prepare();
            controller.play();
        } else if (state == Player.STATE_ENDED) {
            controller.seekTo(controller.getCurrentMediaItemIndex(), 0);
            controller.prepare();
            controller.play();
        } else if (controller.isPlaying()) {
            controller.pause();
        } else {
            controller.play();
        }
    }

    private void playPrevious() {
        if (controller == null) return;
        if (songs.isEmpty()) {
            toast("列表里还没有歌");
            return;
        }
        if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem();
            controller.prepare();
            controller.play();
        } else {
            toast("已经是第一首了");
        }
    }

    private void playNext() {
        if (controller == null) return;
        if (songs.isEmpty()) {
            toast("列表里还没有歌");
            return;
        }
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem();
            controller.prepare();
            controller.play();
        } else {
            toast("已经是最后一首了");
        }
    }

    // ---------- 升降调 ----------

    private void changeSemitones(int delta) {
        changeSemitonesTo(delta == 0 ? 0 : PitchUtils.clamp(semitones + delta));
    }

    private void changeSemitonesTo(int target) {
        semitones = PitchUtils.clamp(target);
        store.saveSemitones(semitones);
        updateKeyLabel();
        applyPitch(semitones, controller != null && controller.isPlaying());
    }

    private void applyPitch(int semis, boolean keepPlaying) {
        if (controller == null) return;
        controller.setPlaybackParameters(
                new PlaybackParameters(1f, PitchUtils.pitchFactor(semis)));
        if (keepPlaying && !controller.isPlaying()) {
            controller.play();
        }
    }

    private void updateKeyLabel() {
        keyLabel.setText("调号：" + PitchUtils.label(semitones));
    }

    // ---------- 从微信 / 文件导入 ----------

    private void handleImportIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        int added = 0;
        int attempted = 0;

        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            attempted++;
            added += importUri(intent.getData());
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                attempted++;
                added += importUri(uri);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null) {
                for (Uri uri : uris) {
                    attempted++;
                    int r = importUri(uri);
                    added += Math.max(r, 0);
                }
            }
        } else {
            return;
        }

        if (attempted == 0) return;
        if (added == attempted) {
            toast(added > 1 ? "已添加 " + added + " 首歌到播放列表" : "已添加到播放列表");
        } else if (added > 0) {
            toast("添加了 " + added + " 首，有 " + (attempted - added) + " 首没能添加");
        } else {
            toast("这首歌没能添加，请再试一次");
        }
    }

    /** 拷贝一个分享来的音频到应用目录；成功返回 1（重复也算成功），不支持返回 0 */
    private int importUri(Uri uri) {
        try {
            String name = queryDisplayName(uri);
            String mime = getContentResolver().getType(uri);
            boolean looksLikeAudio = PitchUtils.isSupportedAudioFile(name)
                    || (mime != null && mime.startsWith("audio/"));
            if (!looksLikeAudio) return 0;

            String title = PitchUtils.titleFromFileName(name);
            for (Song s : songs) {
                if (s.title.equals(title)) return 1; // 已经在列表里
            }

            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return 0;
            File copied = store.copyImportedFile(name, in);

            Song song = new Song(copied.getAbsolutePath(), title);
            songs.add(song);
            store.save(songs);
            adapter.setSongs(songs);
            refreshEmptyState();

            if (controller != null) {
                boolean queueEmpty = controller.getCurrentTimeline().isEmpty();
                controller.addMediaItem(buildMediaItem(song));
                if (queueEmpty) {
                    controller.prepare();
                    controller.play();
                }
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (!TextUtils.isEmpty(name)) return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : ("歌曲_" + System.currentTimeMillis() + ".mp3");
    }

    // ---------- 删除歌曲 ----------

    private void confirmDeleteSong(int position) {
        if (position < 0 || position >= songs.size()) return;
        final Song song = songs.get(position);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除歌曲")
                .setMessage("把《" + song.title + "》从列表里删掉吗？")
                .setPositiveButton("删掉", (d, which) -> deleteSong(position))
                .setNegativeButton("先不删", null)
                .show();
        enlargeDialogText(dialog);
    }

    private void deleteSong(int position) {
        if (position < 0 || position >= songs.size()) return;
        songs.remove(position);
        store.save(songs);
        adapter.setSongs(songs);
        refreshEmptyState();

        if (controller != null && !controller.getCurrentTimeline().isEmpty()) {
            controller.removeMediaItem(position);
        }
        if (songs.isEmpty()) {
            currentTitle.setText(R.string.empty_playlist_hint);
            adapter.setCurrentPlaying(-1);
        }
        toast("已删除");
    }

    // ---------- 界面刷新 ----------

    private void updateTitleFromPlayer() {
        if (controller == null || controller.getCurrentMediaItem() == null) {
            if (songs.isEmpty()) {
                currentTitle.setText(R.string.empty_playlist_hint);
            }
            return;
        }
        MediaMetadata meta = controller.getCurrentMediaItem().mediaMetadata;
        String title = meta != null && !TextUtils.isEmpty(meta.title)
                ? String.valueOf(meta.title) : "未知歌曲";
        currentTitle.setText(title);
    }

    private void updatePlayPauseButton() {
        if (controller == null) {
            btnPlayPause.setText("播放");
            return;
        }
        btnPlayPause.setText(controller.isPlaying() ? "暂停" : "播放");
    }

    private void updateProgressUi() {
        if (controller == null || userSeeking) return;
        long duration = controller.getDuration();
        long position = controller.getCurrentPosition();
        if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) {
            seekBar.setMax((int) duration);
            timeTotal.setText(formatTime(duration));
        } else {
            seekBar.setMax(0);
            timeTotal.setText("0:00");
        }
        seekBar.setProgress((int) Math.max(0, position));
        timeNow.setText(formatTime(position));
    }

    private void refreshEmptyState() {
        if (songs.isEmpty()) {
            currentTitle.setText(R.string.empty_playlist_hint);
        }
    }

    // ---------- 帮助 ----------

    private void showHelpDialog(boolean firstLaunch) {
        String msg = "怎么把微信里的歌加进来：\n"
                + "1. 在微信里点开朋友发的音乐\n"
                + "2. 点右上角「···」\n"
                + "3. 点「用其他应用打开」\n"
                + "4. 点「爷爷的音乐盒」\n\n"
                + "也可以点绿色「＋ 添加歌曲」按钮，从手机里选歌。\n\n"
                + "跟着伴奏唱歌：唱不上去就点「升调」，\n"
                + "唱得太低就点「降调」，\n"
                + "点「原调」就回到原来的调子。";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(firstLaunch ? "欢迎使用" : "使用帮助")
                .setMessage(msg)
                .setPositiveButton("知道啦", null)
                .show();
        enlargeDialogText(dialog);
    }

    /** 对话框文字也用大字，老人看得清 */
    private void enlargeDialogText(AlertDialog dialog) {
        try {
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) message.setTextSize(24);
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) positive.setTextSize(24);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) negative.setTextSize(24);
        } catch (Exception ignored) {
        }
    }

    // ---------- 其他 ----------

    private List<MediaItem> buildMediaItems() {
        List<MediaItem> items = new ArrayList<>();
        for (Song s : songs) items.add(buildMediaItem(s));
        return items;
    }

    private MediaItem buildMediaItem(Song song) {
        return new MediaItem.Builder()
                .setUri(Uri.fromFile(new File(song.path)))
                .setMediaId(song.path)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(song.title).build())
                .build();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + ":" + String.format(java.util.Locale.US, "%02d", seconds);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (controller != null && !songs.isEmpty()) {
            int index = controller.getCurrentMediaItemIndex();
            index = Math.max(0, Math.min(songs.size() - 1, index));
            long position = Math.max(0, controller.getCurrentPosition());
            store.savePlaybackState(index, (int) position, controller.isPlaying());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
        if (controller != null) {
            controller.release();
            controller = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }
}
