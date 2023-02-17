package com.bytedance.krcview;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
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

    public long endTimeMs() {
        return startTimeMs + durationMs;
    }

    public String text;
    @NonNull
    public List<Word> words = Collections.emptyList();
    KrcLineInfo next;

    @Override
    public int compareTo(Long progress) {

        if (next == null || (progress >= startTimeMs && progress < next.startTimeMs)) {
            return 0;
        }
        if (progress < startTimeMs) {
            return 1;
        }
        return -1;

    }

    @Keep
    public static class Word implements Comparable<Long> {

        public long startTimeMs;
        public long duration;
        public String text;
        float previousWordsWidth;
        float textWidth;
        Word next;


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
    }

}
