package com.greenhome.musicbox;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 播放列表的行视图：大字歌名 + 删除按钮，正在播放的行高亮显示 */
public class SongAdapter extends BaseAdapter {

    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final List<Song> songs = new ArrayList<>();
    private int currentPlaying = -1;
    private final OnDeleteClickListener deleteListener;

    public SongAdapter(Context context, OnDeleteClickListener deleteListener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.deleteListener = deleteListener;
    }

    public void setSongs(List<Song> songs) {
        this.songs.clear();
        this.songs.addAll(songs);
        notifyDataSetChanged();
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setCurrentPlaying(int position) {
        if (currentPlaying != position) {
            currentPlaying = position;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return songs.size();
    }

    @Override
    public Song getItem(int position) {
        return songs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        ViewHolder holder;
        if (row == null) {
            row = inflater.inflate(R.layout.item_song, parent, false);
            holder = new ViewHolder();
            holder.title = row.findViewById(R.id.songTitle);
            holder.delete = row.findViewById(R.id.btnDelete);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        final Song song = getItem(position);
        boolean playing = (position == currentPlaying);

        String prefix = playing ? "▶ " : "";
        holder.title.setText(prefix + song.title);
        holder.title.setTextColor(context.getColor(playing ? R.color.primaryGreen : R.color.textDark));
        holder.title.setTypeface(null, playing ? Typeface.BOLD : Typeface.NORMAL);
        holder.title.setContentDescription("第" + (position + 1) + "首，" + song.title);

        holder.delete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(position);
        });

        return row;
    }

    private static class ViewHolder {
        TextView title;
        Button delete;
    }
}
