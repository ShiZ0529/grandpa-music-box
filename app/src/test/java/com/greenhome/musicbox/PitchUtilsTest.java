package com.greenhome.musicbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 半音升降调换算逻辑的单元测试 */
public class PitchUtilsTest {

    private static final float EPS = 1e-4f;

    @Test
    public void zeroSemitones_isOriginalPitch() {
        assertEquals(1.0f, PitchUtils.pitchFactor(0), EPS);
    }

    @Test
    public void twelveSemitones_isOneOctaveUp() {
        assertEquals(2.0f, PitchUtils.pitchFactor(12), EPS);
    }

    @Test
    public void minusTwelveSemitones_isOneOctaveDown() {
        assertEquals(0.5f, PitchUtils.pitchFactor(-12), EPS);
    }

    @Test
    public void oneSemitone_isSemitoneRatio() {
        assertEquals(1.0594631f, PitchUtils.pitchFactor(1), EPS);
        assertEquals(1f / 1.0594631f, PitchUtils.pitchFactor(-1), EPS);
    }

    @Test
    public void clampLimitsRange() {
        assertEquals(12, PitchUtils.clamp(13));
        assertEquals(12, PitchUtils.clamp(12));
        assertEquals(-12, PitchUtils.clamp(-15));
        assertEquals(-12, PitchUtils.clamp(-12));
        assertEquals(0, PitchUtils.clamp(0));
        assertEquals(3, PitchUtils.clamp(3));
    }

    @Test
    public void labelDescribesChange() {
        assertEquals("原调", PitchUtils.label(0));
        assertEquals("升 3 个半音", PitchUtils.label(3));
        assertEquals("降 2 个半音", PitchUtils.label(-2));
    }

    @Test
    public void recognizesAudioFileExtensions() {
        assertTrue(PitchUtils.isSupportedAudioFile("朋友.MP3"));
        assertTrue(PitchUtils.isSupportedAudioFile("黄梅戏选段.mp3"));
        assertTrue(PitchUtils.isSupportedAudioFile("song.wav"));
        assertTrue(PitchUtils.isSupportedAudioFile("a.flac"));
        assertTrue(PitchUtils.isSupportedAudioFile("b.m4a"));
        assertTrue(PitchUtils.isSupportedAudioFile("c.amr"));
        assertFalse(PitchUtils.isSupportedAudioFile("照片.jpg"));
        assertFalse(PitchUtils.isSupportedAudioFile("文档.pdf"));
        assertFalse(PitchUtils.isSupportedAudioFile("noext"));
        assertFalse(PitchUtils.isSupportedAudioFile(null));
    }

    @Test
    public void titleFromFileNameStripsExtension() {
        assertEquals("朋友", PitchUtils.titleFromFileName("朋友.MP3"));
        assertEquals("天仙配", PitchUtils.titleFromFileName("天仙配.mp3"));
        assertEquals("未知歌曲", PitchUtils.titleFromFileName(""));
        assertEquals("未知歌曲", PitchUtils.titleFromFileName(null));
    }
}
