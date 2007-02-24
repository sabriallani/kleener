package com.sixlegs.kleener;

import java.util.*;

public class CharSetBuilder
{
    private final List<Range> ranges = Generics.newArrayList();
    private final Range key = new Range();
    
    private static class Range
    implements Comparable<Range>
    {
        int start;
        int end;
        
        public int compareTo(Range r) {
            return start - r.start;
        }

        public String toString() {
            return "[" + start + ", " + end + "]";
        }
    }

    public CharSetBuilder add(CharSequence chars) {
        for (int i = 0, len = chars.length(); i < len; i++)
            add(chars.charAt(i));
        return this;
    }

    public CharSetBuilder add(CharSet cset) {
        for (int c = cset.nextChar(0); c >= 0; c = cset.nextChar(c + 1))
            add((char)c);
        return this;
    }
    
    public CharSetBuilder add(char start, char end) {
        assert start <= end;
        for (char c = start; c <= end; c++)
            add(c);
        return this;
    }

    // TODO: performance
    public CharSetBuilder add(char c) {
        key.start = c;
        int index = Collections.binarySearch(ranges, key);
        if (index >= 0)
            return this;
        index = -(index + 1);
        if (index > 0) {
            Range r = ranges.get(index - 1);
            if (r.end >= c)
                return this;
            if (r.end + 1 == c) {
                r.end++;
                return this;
            }
        }
        if (index < ranges.size()) {
            Range r = ranges.get(index);
            if (r.start <= c)
                return this;
            if (r.start - 1 == c) {
                r.start--;
                return this;
            }
        }
        Range r = new Range();
        r.start = r.end = c;
        ranges.add(index, r);
        return this;
    }

    public String toString() {
        return ranges.toString();
    }

    public CharSet build() {
        try {
            switch (ranges.size()) {
            case 0:
                return EmptyCharSet.INSTANCE;
            case 1:
                Range r = ranges.get(0);
                if (r.start == r.end)
                    return new SingleCharSet(r.start);
            }
            return new RangeCharSet(new ArrayList<Range>(ranges));
        } finally {
            ranges.clear();
        }
    }

    private static class RangeCharSet extends CharSet
    {
        private final List<Range> ranges;
        private final int length;
        private final Range key = new Range();
        
        public RangeCharSet(List<Range> ranges) {
            this.ranges = ranges;
            length = ranges.get(ranges.size() - 1).end;
        }

        public boolean contains(char c) {
            key.start = c;
            int index = Collections.binarySearch(ranges, key);
            if (index >= 0)
                return true;
            if (index == -1)
                return false;
            Range r = ranges.get(-index - 2);
            return c >= r.start && c <= r.end;
        }

        public int nextChar(int c) {
            key.start = c;
            int index = Collections.binarySearch(ranges, key);
            if (index >= 0)
                return c;
            index = -(index + 1);
            if (index > 0 && ranges.get(index - 1).end >= c)
                return c;
            if (index == ranges.size())
                return -1;
            return ranges.get(index).start;
        }

        // TODO: provide more efficient impl if cset is also range
        public CharSet intersect(CharSet cset) {
            if (cset.isEmpty())
                return cset;
            // TODO: knowing cardinality would help here (want to iterate over smaller one)
            CharSetBuilder builder = new CharSetBuilder();
            for (int c = cset.nextChar(0); c >= 0; c = cset.nextChar(c + 1)) {
                if (contains((char)c))
                    builder.add((char)c);
            }
            return builder.build();
        }

        // TODO: provide more efficient impl if cset is also range
        public CharSet subtract(CharSet cset) {
            if (cset.isEmpty())
                return this;
            CharSetBuilder builder = new CharSetBuilder();
            for (int c = nextChar(0); c >= 0; c = nextChar(c + 1)) {
                if (!cset.contains((char)c))
                    builder.add((char)c);
            }
            return builder.build();
        }
    }
}
