package com.greenhome.musicbox;

import java.util.ArrayList;
import java.util.List;

/** 一首歌：本地拷贝的路径 + 显示的歌名 */
public class Song {
    public final String path;   // 应用私有目录里的文件路径
    public final String title;  // 显示用歌名

    public Song(String path, String title) {
        this.path = path;
        this.title = title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song)) return false;
        return path.equals(((Song) o).path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return title;
    }

    public static List<Song> listOf(Song... songs) {
        List<Song> list = new ArrayList<>();
        for (Song s : songs) list.add(s);
        return list;
    }
}
