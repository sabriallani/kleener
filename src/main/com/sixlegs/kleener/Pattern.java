package com.sixlegs.kleener;

import java.util.*;
import java.util.regex.MatchResult;

abstract public class Pattern
{
    /*
    public static final int CANON_EQ = java.util.regex.Pattern.CANON_EQ;
    public static final int CASE_INSENSITIVE = java.util.regex.Pattern.CASE_INSENSITIVE;
    public static final int COMMENTS = java.util.regex.Pattern.COMMENTS;
    public static final int DOTALL = java.util.regex.Pattern.DOTALL;
    public static final int LITERAL = java.util.regex.Pattern.LITERAL;
    public static final int MULTILINE = java.util.regex.Pattern.MULTILINE;
    public static final int UNICODE_CASE = java.util.regex.Pattern.UNICODE_CASE;
    public static final int UNIX_LINES = java.util.regex.Pattern.UNIX_LINES;
    */

    private static final Sub EMPTY_SUB = new Sub(-1, -1);
    private static final Sub START_SUB = new Sub(0, -1);
    private static final Sub[][] EMPTY = {};

    private final String pattern;
    private final int flags;

    private final State start;
    private final int stateCount;
    private final int parenCount;
    private final State[] states;

    protected Pattern(Expression e, String pattern, int flags) {
        this.pattern = pattern;
        this.flags = flags;

        if (e.getStart().getOp() != State.Op.LParen)
            throw new IllegalArgumentException("Outer expression must be paren");
        this.start = e.getStart();
        e.patch(State.MATCH);

        Set<State> states = new LinkedHashSet<State>();
        states.add(State.MATCH);
        
        this.parenCount = visit(start, states);
        this.stateCount = states.size();
        this.states = states.toArray(new State[stateCount]);
    }

    public static Pattern compile(String regex) {
        return compile(regex, 0);
    }

    public static Pattern compile(String regex, int flags) {
        return new DFA(new ExpressionParser().parse(regex, flags), regex, flags);
    }

    public static boolean matches(String regex, CharSequence input) {
        return compile(regex).matcher(input).matches();
    }

    public static String quote(String s) {
        // TODO
        throw new UnsupportedOperationException("implement me");
    }

    public int flags() {
        return flags;
    }

    public Matcher matcher(CharSequence input) {
        return createMatcher().reset(input);
    }

    abstract protected Matcher createMatcher();

    public String pattern() {
        return pattern;
    }

    @Override public String toString() {
        return pattern();
    }

    public String[] split(CharSequence input) {
        return split(input, 0);
    }

    public String[] split(CharSequence input, int limit) {
        List<String> parts = new ArrayList<String>((limit <= 0) ? 10 : limit);
        Matcher m = matcher(input);
        int p = 0;
        for (int count = 0; ++count != limit && m.find(p); p = m.end())
            parts.add(input.subSequence(p, m.start()).toString());
        parts.add(input.subSequence(p, input.length()).toString());
        if (limit == 0) {
            int size = parts.size();
            while ("".equals(parts.get(size - 1)))
                parts.remove(--size);
        }
        return parts.toArray(new String[parts.size()]);
    }

    private static int visit(State state, Set<State> mark) {
        if (state == null || mark.contains(state))
            return 0;
        state.setId(mark.size());
        mark.add(state);
        return ((state.getOp() == State.Op.LParen) ? 1 : 0) +
            visit(state.getState1(), mark) +
            visit(state.getState2(), mark);
    }

    protected State startState() {
        return start;
    }

    protected int parenCount() {
        return parenCount;
    }

    protected int stateCount() {
        return stateCount;
    }
    
    protected void startSet(int p, Sub[][] threads) {
        step(EMPTY, 0, p, threads, new Sub[parenCount]);
    }

    protected void step(Sub[][] clist, int c, int p, Sub[][] nlist, Sub[] match) {
        Arrays.fill(nlist, null);
        for (int i = 0; i < clist.length; i++) {
            Sub[] tmatch = clist[i];
            if (tmatch == null)
                continue;
            State state = states[i];
            switch (state.getOp()) {
            case CharSet:
                if (state.getCharSet().contains(c))
                    addState(nlist, state.getState1(), tmatch, p);
                break;
            case NotCharSet:
                if (!state.getCharSet().contains(c))
                    addState(nlist, state.getState1(), tmatch, p);
                break;
            case Match:
                System.arraycopy(tmatch, 0, match, 0, parenCount);
                return;
            }
        }
        if (match[0] == null || match[0].sp < 0)
            addState(nlist, start, new Sub[parenCount], p);
    }

    private void addState(Sub[][] threads, State state, Sub[] match, int p) {
        if (state == null)
            return;
        int id = state.getId();
        if (threads[id] != null)
            return;

        threads[id] = new Sub[parenCount];
        System.arraycopy(match, 0, threads[id], 0, parenCount);
        
        switch (state.getOp()) {
        case Match:
        case CharSet:
        case NotCharSet:
            break;
        case Split:
            addState(threads, state.getState1(), match, p);
            addState(threads, state.getState2(), match, p);
            break;
        default:
            int data = state.getData();
            Sub save = match[data];
            match[data] = (state.getOp() == State.Op.LParen) ?
                ((p == 0) ? START_SUB : new Sub(p, -1)) :
                new Sub(save.sp, p);
            addState(threads, state.getState1(), match, p);
            match[data] = save;
        }
    }
}
