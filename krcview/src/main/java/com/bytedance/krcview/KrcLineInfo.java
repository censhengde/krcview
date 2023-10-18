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
public class KrcLineInfo implements Comparable<Long>, Parcelable {


    public static final Creator<KrcLineInfo> CREATOR = new Creator<KrcLineInfo>() {
        @Override
        public KrcLineInfo createFromParcel(Parcel in) {
            return new KrcLineInfo(in);
        }

        @Override
        public KrcLineInfo[] newArray(int size) {
            return new KrcLineInfo[size];
        }
    };

    public long startTimeMs;
    public long durationMs;
    public String text;
    public List<Word> words = Collections.emptyList();
    public KrcLineInfo nextKrcLineInfo;

    // 用户扩展字段，方便用户赋予其他信息。
    private Bundle userExt;


    protected KrcLineInfo(Parcel in) {
        startTimeMs = in.readLong();
        durationMs = in.readLong();
        text = in.readString();
        nextKrcLineInfo = in.readParcelable(KrcLineInfo.class.getClassLoader());
        words = new ArrayList<>();
        in.readList(words, Word.class.getClassLoader());
    }

    public KrcLineInfo() {
    }

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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(startTimeMs);
        dest.writeLong(durationMs);
        dest.writeString(text);
        dest.writeParcelable(nextKrcLineInfo, flags);
        dest.writeList(words);
    }

    public static class Word implements Comparable<Long>, Parcelable {

        public static final Creator<Word> CREATOR = new Creator<Word>() {
            @Override
            public Word createFromParcel(Parcel in) {
                return new Word(in);
            }

            @Override
            public Word[] newArray(int size) {
                return new Word[size];
            }
        };
        public long startTimeMs;
        public long duration;
        public String text;
        public float previousWordsWidth;
        public float textWidth;

        public Word next;
        private Bundle userExt;


        protected Word(Parcel in) {
            startTimeMs = in.readLong();
            duration = in.readLong();
            text = in.readString();
            previousWordsWidth = in.readFloat();
            textWidth = in.readFloat();
            next = in.readParcelable(Word.class.getClassLoader());
        }

        public Word() {
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

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(startTimeMs);
            dest.writeLong(duration);
            dest.writeString(text);
            dest.writeFloat(previousWordsWidth);
            dest.writeFloat(textWidth);
            dest.writeParcelable(next, flags);
        }
    }
}
