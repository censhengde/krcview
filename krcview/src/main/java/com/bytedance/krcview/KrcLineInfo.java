package com.bytedance.krcview;

import java.util.List;

/**
 * Author：Shengde·Cen on 2023/2/15 14:14
 *
 * explain：
 */
public class KrcLineInfo implements Comparable<Long> {

    public long startTimeMs;
    public long durationMs;

    public long endTimeMs() {
        return startTimeMs + durationMs;
    }

    public String text;
    public List<Word> words;
    public KrcLineInfo nextKrcLineInfo;

    @Override
    public int compareTo(Long progress) {

        if (nextKrcLineInfo == null || (progress >= startTimeMs && progress < nextKrcLineInfo.startTimeMs)) {
            return 0;
        }
        if (progress < startTimeMs) {
            return 1;
        }
        return -1;

    }

    public static class Word implements Comparable<Long> {

        public long startTimeMs;
        public long duration;
        public String text;
        public float previousWordsWidth;
        public float textWidth;

        public Word next;


        @Override
        public int compareTo(Long progress) {
            if (next == null) {
                return 0;
            }
            if (progress >= startTimeMs && progress < next.startTimeMs) {
                return 0;
            }
            if (progress < startTimeMs) {
                return 1;
            }
            return -1;
        }

        public long endTimeMs() {
            return startTimeMs + duration;
        }
    }

}
