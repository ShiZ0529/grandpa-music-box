package com.greenhome.musicbox;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 播放列表与播放进度的本地持久化。
 * 列表存 JSON 文件，进度/调号存 SharedPreferences，全部在应用私有目录，
 * 不需要任何存储权限。
 */
public class PlaylistStore {

    private static final String PLAYLIST_FILE = "playlist.json";
    private static final String PREFS = "settings";

    private final Context context;
    private final File playlistFile;

    public PlaylistStore(Context context) {
        this.context = context.getApplicationContext();
        this.playlistFile = new File(this.context.getFilesDir(), PLAYLIST_FILE);
    }

    // ---------- 播放列表 ----------

    public List<Song> load() {
        List<Song> songs = new ArrayList<>();
        if (!playlistFile.exists()) return songs;
        try {
            String json = readFile(playlistFile);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String path = o.optString("path", "");
                String title = o.optString("title", "未知歌曲");
                if (!path.isEmpty() && new File(path).exists()) {
                    songs.add(new Song(path, title));
                }
            }
        } catch (Exception ignored) {
            // 文件坏了就从空列表开始，别让老人打不开应用
        }
        return songs;
    }

    public void save(List<Song> songs) {
        try {
            JSONArray arr = new JSONArray();
            for (Song s : songs) {
                JSONObject o = new JSONObject();
                o.put("path", s.path);
                o.put("title", s.title);
                arr.put(o);
            }
            writeFile(playlistFile, arr.toString());
        } catch (Exception ignored) {
        }
    }

    /** 把微信等分享来的音频拷进应用私有目录，返回可播放的本地文件 */
    public File copyImportedFile(String displayName, java.io.InputStream in) throws IOException {
        File dir = new File(context.getFilesDir(), "music");
        if (!dir.exists()) dir.mkdirs();
        String safeName = sanitize(displayName);
        File out = new File(dir, safeName);
        int n = 2;
        while (out.exists()) {
            int dot = safeName.lastIndexOf('.');
            if (dot > 0) {
                out = new File(dir, safeName.substring(0, dot) + "_" + n + safeName.substring(dot));
            } else {
                out = new File(dir, safeName + "_" + n);
            }
            n++;
        }
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[64 * 1024];
        try {
            int r;
            while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
        } finally {
            try { in.close(); } catch (IOException ignored) {}
            try { fos.close(); } catch (IOException ignored) {}
        }
        return out;
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "歌曲_" + System.currentTimeMillis() + ".mp3";
        }
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }

    // ---------- 播放设置 ----------

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int loadSemitones() {
        return prefs().getInt("semitones", 0);
    }

    public void saveSemitones(int semitones) {
        prefs().edit().putInt("semitones", PitchUtils.clamp(semitones)).apply();
    }

    public int loadLastIndex() {
        return prefs().getInt("lastIndex", 0);
    }

    public int loadLastPositionMs() {
        return prefs().getInt("lastPositionMs", 0);
    }

    public boolean loadWasPlaying() {
        return prefs().getBoolean("wasPlaying", false);
    }

    public boolean loadHelpShown() {
        return prefs().getBoolean("helpShown", false);
    }

    public void savePlaybackState(int index, int positionMs, boolean wasPlaying) {
        prefs().edit()
                .putInt("lastIndex", index)
                .putInt("lastPositionMs", positionMs)
                .putBoolean("wasPlaying", wasPlaying)
                .apply();
    }

    public void saveHelpShown() {
        prefs().edit().putBoolean("helpShown", true).apply();
    }

    // ---------- IO ----------

    private static String readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = fis.read(buf)) > 0) bos.write(buf, 0, r);
        fis.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeFile(File f, String content) throws IOException {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(content.getBytes(StandardCharsets.UTF_8));
        fos.close();
    }
}
