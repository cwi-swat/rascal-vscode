package org.rascalmpl.vscode.lsp.util.locations.impl;

import java.util.ArrayList;
import java.util.Arrays;

import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;

public class ArrayLineOffsetMap implements LineColumnOffsetMap {
    private final IntArray lines;
    private final ArrayList<IntArray> wideColumnOffsets;

    public ArrayLineOffsetMap(IntArray lines, ArrayList<IntArray> wideColumnOffsets) {
        this.lines = lines;
        this.wideColumnOffsets = wideColumnOffsets;
    }

    @Override
    public int translateColumn(int line, int column, boolean isEnd) {
        int lineIndex = lines.search(line);
        if (lineIndex < 0) {
            return column;
        }
        IntArray lineOffsets = wideColumnOffsets.get(lineIndex);
        int columnIndex = lineOffsets.search(column);
        if (columnIndex >= 0) {
            // exact hit
            if (isEnd) {
                // for a end cursor, we want to count this char twice as well
                columnIndex++;
            }
        }
        else {
            columnIndex = Math.abs(columnIndex + 1);
        }
        // now we know how many
        return column + columnIndex;
    }


    @SuppressWarnings("java:S3776") // parsing tends to be complex
    public static LineColumnOffsetMap build(String contents) {
        int line = 0;
        int column = 0;
        char prev = '\0';
        GrowingIntArray linesWithSurrogate = new GrowingIntArray();
        ArrayList<IntArray> linesMap = new ArrayList<>(0);
        GrowingIntArray currentLine = new GrowingIntArray();

        for(int i = 0, n = contents.length() ; i < n ; i++) {
            char c = contents.charAt(i);
            if (c == '\n' || c == '\r') {
                if (c != prev && (prev == '\r' || prev == '\n')) {
                    continue; // multichar newline skip it
                }
                if (!currentLine.isEmpty()) {
                    linesWithSurrogate.add(line);
                    linesMap.add(currentLine.build());
                    currentLine = new GrowingIntArray();
                }
                line++;
                column = 0;
            }
            else {
                column++;
                if (Character.isHighSurrogate(c) && (i + 1) < n && Character.isLowSurrogate(contents.charAt(i + 1))) {
                    // full surrogate pair, register it, and skip the next char
                    currentLine.add(column);
                    i++;
                }
            }
            prev = c;
        }
        if (!currentLine.isEmpty()) {
            // handle last line
            linesWithSurrogate.add(line);
            linesMap.add(currentLine.build());
        }
        if (linesMap.isEmpty()) {
            return EMPTY_MAP;
        }
        return new ArrayLineOffsetMap(linesWithSurrogate.build(), linesMap);
    }

    private static LineColumnOffsetMap EMPTY_MAP = new LineColumnOffsetMap(){
        @Override
        public int translateColumn(int line, int column, boolean atEnd) {
            return column;
        }
    };


    private static class GrowingIntArray {
        private int[] data = new int[0];
        private int filled = 0;

        public void add(int v) {
            growIfNeeded();
            data[filled] = v;
            filled++;
        }

        public boolean isEmpty() {
            return filled == 0;
        }

        public IntArray build() {
            return new IntArray(data, filled);
        }

        private void growIfNeeded() {
            if (filled >= data.length) {
                if (data.length == 0) {
                    data = new int[4];
                }
                else {
                    data = Arrays.copyOf(data, data.length + (data.length / 2));
                }
            }
        }
    }

    private static class IntArray {
        private final int[] data;
        private final int length;

        public IntArray(int[] data, int length) {
            this.data = data;
            this.length = length;
        }

        /**
         * search for key, assume it's sorted data
         * @return >= 0 in case of exact match, below 0 is the insert point
         */
        public int search(int key) {
            if (length <= 8) {
                // small array, just linear search
                for (int i = 0; i < length; i++) {
                    if (data[i] >= key) {
                        if (data[i] == key) {
                            return i;
                        }
                        return (-i) - 1;
                    }
                }
                return -(length + 1);
            }
            return Arrays.binarySearch(data, 0, length, key);
        }
    }


}