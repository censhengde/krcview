package com.bytedance.krcview;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Author：Shengde·Cen on 2023/2/15 14:14
 *
 * explain：
 */
@Keep
public class KrcLineInfo implements Comparable<Long> {

    public long startTimeMs;
    public long durationMs;
    public String text;
    public List<Word> words = Collections.emptyList();
    public KrcLineInfo nextKrcLineInfo;

    // 用户扩展字段，方便用户赋予其他信息。
    private Bundle userExt;

    public long endTimeMs() {
        return startTimeMs + durationMs;
    }

    @NonNull
    public Bundle getUserExt() {
        if (userExt == null) {
            userExt = new Bundle();
        }
        return userExt;
    }

    @Override
    public int compareTo(Long progress) {
        if (nextKrcLineInfo == null
                || (progress >= startTimeMs && progress < nextKrcLineInfo.startTimeMs)) {
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
        private Bundle userExt;

        @NonNull
        public Bundle getUserExt() {
            if (userExt == null) {
                userExt = new Bundle();
            }
            return userExt;
        }

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
