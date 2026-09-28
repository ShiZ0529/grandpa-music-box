package com.greenhome.musicbox;

/**
 * 半音升降调工具：把“升/降 N 个半音”换算成播放器的 pitch 值。
 * pitch = 2^(半音数/12)，Media3/ExoPlayer 会用内置 Sonic 算法
 * 做“变调不变速”处理，速度保持原样，适合跟着伴奏唱。
 */
public final class PitchUtils {

    public static final int MIN_SEMITONES = -12;
    public static final int MAX_SEMITONES = 12;

    private PitchUtils() {}

    /** 把半音数限制在 ±12 范围内 */
    public static int clamp(int semitones) {
        return Math.max(MIN_SEMITONES, Math.min(MAX_SEMITONES, semitones));
    }

    /** 半音数 -> pitch 因子（1.0 = 原调） */
    public static float pitchFactor(int semitones) {
        return (float) Math.pow(2.0, semitones / 12.0);
    }

    /** 给老人看的调号说明文字 */
    public static String label(int semitones) {
        if (semitones == 0) return "原调";
        if (semitones > 0) return "升 " + semitones + " 个半音";
        return "降 " + (-semitones) + " 个半音";
    }

    /** 从文件名取出歌名（去掉扩展名） */
    public static String titleFromFileName(String fileName) {
        if (fileName == null) return "未知歌曲";
        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        name = name.trim();
        return name.isEmpty() ? "未知歌曲" : name;
    }

    /** 是否为支持的音乐扩展名 */
    public static boolean isSupportedAudioFile(String fileName) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = fileName.substring(dot + 1).toLowerCase(java.util.Locale.US);
        switch (ext) {
            case "mp3":
            case "wav":
            case "flac":
            case "m4a":
            case "aac":
            case "ogg":
            case "oga":
            case "opus":
            case "amr":
            case "mid":
            case "xmf":
                return true;
            default:
                return false;
        }
    }
}
