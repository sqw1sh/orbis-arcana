package com.orbisarcana.client.vfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.JsonElement;

/**
 * A small Molang subset for Snowstorm effects (roadmap V4): numbers, {@code variable.x}/{@code v.x} and
 * {@code query.x}/{@code q.x} (variables live in slots of a {@link Scope}), {@code + - * /}, comparisons, {@code && ||
 * !}, {@code ?:}, parentheses, {@code math.*} (sin/cos take degrees, as in Molang), assignments {@code v.x = ...;} and
 * {@code return}. Unknown names read as 0. Compiled once into a tree, evaluated per emitter or particle.
 */
final class Molang {
    interface Expr {
        double eval(Scope s);
    }

    /** Variable values of one emitter or particle, by slot. */
    static final class Scope {
        double[] values = new double[SLOTS.size() + 8];

        double get(int slot) {
            return slot < values.length ? values[slot] : 0;
        }

        void set(int slot, double value) {
            if (slot >= values.length) values = java.util.Arrays.copyOf(values, Math.max(slot + 8, values.length * 2));
            values[slot] = value;
        }

        /** Copies the emitter's variables under a particle's own. */
        void inherit(Scope parent) {
            if (values.length < parent.values.length) values = java.util.Arrays.copyOf(values, parent.values.length);
            System.arraycopy(parent.values, 0, values, 0, parent.values.length);
        }
    }

    private static final Map<String, Integer> SLOTS = new HashMap<>();

    /** Slot of a variable name such as {@code particle_age} (variable./query. prefixes stripped). */
    static synchronized int slot(String name) {
        return SLOTS.computeIfAbsent(name, k -> SLOTS.size());
    }

    static final Expr ZERO = s -> 0;

    static Expr constant(double v) {
        return s -> v;
    }

    /** A JSON number or Molang string; null or missing reads as {@code fallback}. */
    static Expr of(JsonElement e, double fallback) {
        if (e == null || e.isJsonNull()) return constant(fallback);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return constant(e.getAsDouble());
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return constant(e.getAsBoolean() ? 1 : 0);
        return compile(e.getAsString());
    }

    static Expr compile(String src) {
        String s = src.trim();
        if (s.isEmpty()) return ZERO;
        try {
            return new Parser(s.toLowerCase(Locale.ROOT)).program();
        } catch (RuntimeException ex) {
            com.orbisarcana.OrbisArcana.LOGGER.warn("Molang: cannot parse '{}': {}", src, ex.getMessage());
            return ZERO;
        }
    }

    private static final class Parser {
        private final String s;
        private int p;

        Parser(String s) {
            this.s = s;
        }

        Expr program() {
            List<Expr> statements = new ArrayList<>();
            Expr last = ZERO;
            boolean multi = s.contains(";");
            while (true) {
                skip();
                if (p >= s.length()) break;
                if (peekWord("return")) {
                    p += 6;
                    last = ternary();
                    statements.add(last);
                } else {
                    last = statement();
                    statements.add(last);
                }
                skip();
                if (p < s.length() && s.charAt(p) == ';') p++;
                else break;
            }
            if (!multi || statements.size() == 1) return last;
            Expr[] all = statements.toArray(Expr[]::new);
            Expr result = last;
            return sc -> {
                for (int i = 0; i < all.length - 1; i++) all[i].eval(sc);
                return result.eval(sc);
            };
        }

        private Expr statement() {
            int save = p;
            String name = identifier();
            skip();
            if (name != null && p < s.length() && s.charAt(p) == '=' && (p + 1 >= s.length() || s.charAt(p + 1) != '=')) {
                p++;
                int slot = slot(strip(name));
                Expr value = ternary();
                return sc -> {
                    double v = value.eval(sc);
                    sc.set(slot, v);
                    return v;
                };
            }
            p = save;
            return ternary();
        }

        private Expr ternary() {
            Expr cond = or();
            skip();
            if (p < s.length() && s.charAt(p) == '?') {
                p++;
                Expr a = ternary();
                skip();
                if (p < s.length() && s.charAt(p) == ':') {
                    p++;
                    Expr b = ternary();
                    return sc -> cond.eval(sc) != 0 ? a.eval(sc) : b.eval(sc);
                }
                return sc -> cond.eval(sc) != 0 ? a.eval(sc) : 0;
            }
            return cond;
        }

        private Expr or() {
            Expr l = and();
            while (match("||")) {
                Expr a = l, b = and();
                l = sc -> a.eval(sc) != 0 || b.eval(sc) != 0 ? 1 : 0;
            }
            return l;
        }

        private Expr and() {
            Expr l = compare();
            while (match("&&")) {
                Expr a = l, b = compare();
                l = sc -> a.eval(sc) != 0 && b.eval(sc) != 0 ? 1 : 0;
            }
            return l;
        }

        private Expr compare() {
            Expr l = additive();
            while (true) {
                Expr a = l;
                if (match("<=")) { Expr b = additive(); l = sc -> a.eval(sc) <= b.eval(sc) ? 1 : 0; }
                else if (match(">=")) { Expr b = additive(); l = sc -> a.eval(sc) >= b.eval(sc) ? 1 : 0; }
                else if (match("==")) { Expr b = additive(); l = sc -> a.eval(sc) == b.eval(sc) ? 1 : 0; }
                else if (match("!=")) { Expr b = additive(); l = sc -> a.eval(sc) != b.eval(sc) ? 1 : 0; }
                else if (match("<")) { Expr b = additive(); l = sc -> a.eval(sc) < b.eval(sc) ? 1 : 0; }
                else if (match(">")) { Expr b = additive(); l = sc -> a.eval(sc) > b.eval(sc) ? 1 : 0; }
                else return l;
            }
        }

        private Expr additive() {
            Expr l = multiplicative();
            while (true) {
                Expr a = l;
                if (match("+")) { Expr b = multiplicative(); l = sc -> a.eval(sc) + b.eval(sc); }
                else if (match("-")) { Expr b = multiplicative(); l = sc -> a.eval(sc) - b.eval(sc); }
                else return l;
            }
        }

        private Expr multiplicative() {
            Expr l = unary();
            while (true) {
                Expr a = l;
                if (match("*")) { Expr b = unary(); l = sc -> a.eval(sc) * b.eval(sc); }
                else if (match("/")) { Expr b = unary(); l = sc -> { double d = b.eval(sc); return d == 0 ? 0 : a.eval(sc) / d; }; }
                else return l;
            }
        }

        private Expr unary() {
            if (match("-")) { Expr e = unary(); return sc -> -e.eval(sc); }
            if (match("!")) { Expr e = unary(); return sc -> e.eval(sc) == 0 ? 1 : 0; }
            if (match("+")) return unary();
            return primary();
        }

        private Expr primary() {
            skip();
            if (p >= s.length()) throw new IllegalStateException("unexpected end");
            char c = s.charAt(p);
            if (c == '(') {
                p++;
                Expr e = ternary();
                expect(')');
                return e;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = p;
                while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++;
                if (p < s.length() && s.charAt(p) == 'f') p++;
                double v = Double.parseDouble(s.substring(start, p).replace("f", ""));
                return constant(v);
            }
            String name = identifier();
            if (name == null) throw new IllegalStateException("unexpected '" + c + "'");
            skip();
            if (name.startsWith("math.")) return math(name.substring(5));
            int slot = slot(strip(name));
            return sc -> sc.get(slot);
        }

        private Expr math(String fn) {
            List<Expr> args = new ArrayList<>();
            if (p < s.length() && s.charAt(p) == '(') {
                p++;
                skip();
                if (p < s.length() && s.charAt(p) != ')') {
                    do args.add(ternary()); while (match(","));
                }
                expect(')');
            }
            Expr a = args.size() > 0 ? args.get(0) : ZERO, b = args.size() > 1 ? args.get(1) : ZERO, c = args.size() > 2 ? args.get(2) : ZERO;
            return switch (fn) {
                case "pi" -> constant(Math.PI);
                case "sin" -> sc -> Math.sin(Math.toRadians(a.eval(sc)));
                case "cos" -> sc -> Math.cos(Math.toRadians(a.eval(sc)));
                case "abs" -> sc -> Math.abs(a.eval(sc));
                case "sqrt" -> sc -> Math.sqrt(Math.max(0, a.eval(sc)));
                case "pow" -> sc -> Math.pow(a.eval(sc), b.eval(sc));
                case "exp" -> sc -> Math.exp(a.eval(sc));
                case "floor" -> sc -> Math.floor(a.eval(sc));
                case "ceil" -> sc -> Math.ceil(a.eval(sc));
                case "round" -> sc -> Math.round(a.eval(sc));
                case "trunc" -> sc -> (double) (long) a.eval(sc);
                case "mod" -> sc -> { double d = b.eval(sc); return d == 0 ? 0 : a.eval(sc) % d; };
                case "min" -> sc -> Math.min(a.eval(sc), b.eval(sc));
                case "max" -> sc -> Math.max(a.eval(sc), b.eval(sc));
                case "clamp" -> sc -> Math.max(b.eval(sc), Math.min(c.eval(sc), a.eval(sc)));
                case "lerp" -> sc -> { double x = a.eval(sc); return x + (b.eval(sc) - x) * c.eval(sc); };
                case "random" -> sc -> { double lo = a.eval(sc); return lo + ThreadLocalRandom.current().nextDouble() * (b.eval(sc) - lo); };
                case "random_integer" -> sc -> { long lo = Math.round(a.eval(sc)), hi = Math.round(b.eval(sc)); return lo + ThreadLocalRandom.current().nextLong(Math.max(1, hi - lo + 1)); };
                case "die_roll" -> sc -> { double sum = 0; int n = (int) a.eval(sc); for (int i = 0; i < n; i++) sum += b.eval(sc) + ThreadLocalRandom.current().nextDouble() * (c.eval(sc) - b.eval(sc)); return sum; };
                default -> ZERO;
            };
        }

        /** Strips variable./v./query./q./temp./t. prefixes. */
        private static String strip(String name) {
            int dot = name.indexOf('.');
            if (dot < 0) return name;
            String head = name.substring(0, dot);
            return switch (head) {
                case "variable", "v", "query", "q", "temp", "t" -> name.substring(dot + 1);
                default -> name;
            };
        }

        private String identifier() {
            skip();
            int start = p;
            while (p < s.length() && (Character.isLetterOrDigit(s.charAt(p)) || s.charAt(p) == '_' || s.charAt(p) == '.')) p++;
            return p > start && !Character.isDigit(s.charAt(start)) ? s.substring(start, p) : null;
        }

        private boolean peekWord(String w) {
            return s.startsWith(w, p) && (p + w.length() >= s.length() || !Character.isLetterOrDigit(s.charAt(p + w.length())));
        }

        private boolean match(String token) {
            skip();
            if (s.startsWith(token, p)) {
                p += token.length();
                return true;
            }
            return false;
        }

        private void expect(char c) {
            skip();
            if (p >= s.length() || s.charAt(p) != c) throw new IllegalStateException("expected '" + c + "'");
            p++;
        }

        private void skip() {
            while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++;
        }
    }

    private Molang() {}
}
