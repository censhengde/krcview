package com.bytedance.demo;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bytedance.krcview.KrcLineInfo;
import com.bytedance.krcview.KrcLineInfo.Word;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author：岑胜德 on 2023/1/4 17:17
 *
 * 说明：
 */
public class KrcParser {

    /**
     * 歌曲名 字符串
     */
    private final static String LEGAL_SONGNAME_PREFIX = "[ti:";
    /**
     * 歌手名 字符串
     */
    private final static String LEGAL_SINGERNAME_PREFIX = "[ar:";
    /**
     * 时间补偿值 字符串
     */
    private final static String LEGAL_OFFSET_PREFIX = "[offset:";
    /**
     * 歌词上传者
     */
    private final static String LEGAL_BY_PREFIX = "[by:";
    private final static String LEGAL_HASH_PREFIX = "[hash:";
    /**
     * 专辑
     */
    private final static String LEGAL_AL_PREFIX = "[al:";
    private final static String LEGAL_SIGN_PREFIX = "[sign:";
    private final static String LEGAL_QQ_PREFIX = "[qq:";
    private final static String LEGAL_TOTAL_PREFIX = "[total:";
    private final static String LEGAL_LANGUAGE_PREFIX = "[language:";


    public static void read(final String path, @NonNull final Listener listener) {
        if (TextUtils.isEmpty(path)) {
            listener.onResult(null);
            return;
        }
        new Thread(() -> {
            try {
                listener.onResult(read(new FileInputStream(path)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                listener.onResult(null);
            }
        }, "krc-parse-thread").start();
    }

    public static List<KrcLineInfo> readFromAsset(Context context, String fileName) {
        if (TextUtils.isEmpty(fileName) || context == null) {
            return null;
        }
        try {
            return read(context.getAssets().open(fileName));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static List<KrcLineInfo> read(InputStream in) {
        if (in == null) {
            return null;
        }

        try {
            final byte[] data = new byte[in.available()];
            in.read(data);
            final String strData = new String(data, Charset.defaultCharset());
            final String[] lyricsTexts = strData.split("\n");
            List<KrcLineInfo> krcLineInfos = new ArrayList<>(lyricsTexts.length);
            for (String lineInfo : lyricsTexts) {
                // 行读取，并解析每行歌词的内容
                KrcLineInfo lyricsLineInfo = parserLineInfo(lineInfo);
                if (lyricsLineInfo != null) {
                    krcLineInfos.add(lyricsLineInfo);
                }
            }
            return krcLineInfos;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 解析歌词
     *
     * @return
     */
    private static KrcLineInfo parserLineInfo(String lineInfo) {
        KrcLineInfo lyricsLineInfo = null;
        if (lineInfo.startsWith(LEGAL_SONGNAME_PREFIX)) {
            int startIndex = LEGAL_SONGNAME_PREFIX.length();
            int endIndex = lineInfo.lastIndexOf("]");
            //
//            lyricsTags.put(LyricsTag.TAG_TITLE,
//                    lineInfo.substring(startIndex, endIndex));
        } else if (lineInfo.startsWith(LEGAL_SINGERNAME_PREFIX)) {
            int startIndex = LEGAL_SINGERNAME_PREFIX.length();
            int endIndex = lineInfo.lastIndexOf("]");
//            lyricsTags.put(LyricsTag.TAG_ARTIST,
//                    lineInfo.substring(startIndex, endIndex));
        } else if (lineInfo.startsWith(LEGAL_OFFSET_PREFIX)) {
            int startIndex = LEGAL_OFFSET_PREFIX.length();
            int endIndex = lineInfo.lastIndexOf("]");
//            lyricsTags.put(LyricsTag.TAG_OFFSET,
//                    lineInfo.substring(startIndex, endIndex));
        } else if (lineInfo.startsWith(LEGAL_BY_PREFIX)
                || lineInfo.startsWith(LEGAL_HASH_PREFIX)
                || lineInfo.startsWith(LEGAL_SIGN_PREFIX)
                || lineInfo.startsWith(LEGAL_QQ_PREFIX)
                || lineInfo.startsWith(LEGAL_TOTAL_PREFIX)
                || lineInfo.startsWith(LEGAL_AL_PREFIX)) {

//            int startIndex = lineInfo.indexOf("[") + 1;
//            int endIndex = lineInfo.lastIndexOf("]");
//            String temp[] = lineInfo.substring(startIndex, endIndex).split(":");
//            lyricsTags.put(temp[0], temp.length == 1 ? "" : temp[1]);

        } else if (lineInfo.startsWith(LEGAL_LANGUAGE_PREFIX)) {
            int startIndex = lineInfo.indexOf("[") + 1;
            int endIndex = lineInfo.lastIndexOf("]");
            String[] temp = lineInfo.substring(startIndex, endIndex).split(":");
            // 解析翻译歌词
            // 获取json base64字符串
            String translateJsonBase64String = temp.length == 1 ? "" : temp[1];
            if (!translateJsonBase64String.equals("")) {

                String translateJsonString = new String(
                        Base64.decode(translateJsonBase64String, Base64.NO_WRAP));
//                parserOtherLrc(lyricsIfno, translateJsonString);
            }
        } else {
            // 匹配歌词行
            Pattern pattern = Pattern.compile("\\[\\d+,\\d+\\]");
            Matcher matcher = pattern.matcher(lineInfo);
            if (matcher.find()) {
                lyricsLineInfo = new KrcLineInfo();
                // [此行开始时刻距0时刻的毫秒数,此行持续的毫秒数]<0,此字持续的毫秒数,0>歌<此字开始的时刻距此行开始时刻的毫秒数,此字持续的毫秒数,0>词<此字开始的时刻距此行开始时刻的毫秒数,此字持续的毫秒数,0>正<此字开始的时刻距此行开始时刻的毫秒数,此字持续的毫秒数,0>文
                // 获取行的出现时间和结束时间
                int mStartIndex = matcher.start();
                int mEndIndex = matcher.end();
                String[] lineTime = lineInfo.substring(mStartIndex + 1,
                        mEndIndex - 1).split(",");
                //

                lyricsLineInfo.startTimeMs = Integer.parseInt(lineTime[0]);
                lyricsLineInfo.durationMs = Integer.parseInt(lineTime[1]);

                // 获取歌词信息
                String lineContent = lineInfo.substring(mEndIndex);

                // 歌词匹配的正则表达式
                String regex = "\\<\\d+,\\d+,\\d+\\>";
                Pattern lyricsWordsPattern = Pattern.compile(regex);
                Matcher lyricsWordsMatcher = lyricsWordsPattern
                        .matcher(lineContent);

                // 歌词分隔
                String[] lineLyricsTemp = lineContent.split(regex);
//                String[] lyricsWords = getLyricsWords(lineLyricsTemp);
                lyricsLineInfo.words = new ArrayList<>(lineLyricsTemp.length);
//                lyricsLineInfo.setLyricsWords(lyricsWords);

                // 获取每个歌词的时间
//                int wordsDisInterval[] = new int[lyricsWords.length];
                int index = 1;
                while (lyricsWordsMatcher.find()) {

                    //
                    String wordsDisIntervalStr = lyricsWordsMatcher.group();
                    String wordsDisIntervalStrTemp = wordsDisIntervalStr
                            .substring(wordsDisIntervalStr.indexOf('<') + 1, wordsDisIntervalStr.lastIndexOf('>'));
                    String[] wordsDisIntervalTemp = wordsDisIntervalStrTemp
                            .split(",");
                    final Word word = new Word();
                    word.startTimeMs = Integer.parseInt(wordsDisIntervalTemp[0]);
                    word.duration = Integer.parseInt(wordsDisIntervalTemp[1]);
                    word.text = lineLyricsTemp[index++];
                    lyricsLineInfo.words.add(word);
//                    wordsDisInterval[index++] = Integer
//                            .parseInt(wordsDisIntervalTemp[1]);
                }
//                lyricsLineInfo.setWordsDisInterval(wordsDisInterval);

                // 获取当行歌词
                lyricsLineInfo.text = lyricsWordsMatcher.replaceAll("");
            }

        }
        return lyricsLineInfo;
    }


    public interface Listener {

        void onResult(@Nullable List<KrcLineInfo> lineInfos);
    }

}
