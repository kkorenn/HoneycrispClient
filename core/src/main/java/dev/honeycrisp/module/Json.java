package dev.honeycrisp.module;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON: objects → LinkedHashMap, arrays → ArrayList, numbers → Double, plus String/Boolean/null. */
public final class Json {
	private final String s;
	private int i;

	private Json(String s) {
		this.s = s;
	}

	public static Object parse(String text) {
		Json p = new Json(text);
		Object v = p.value();
		p.ws();
		if (p.i != text.length()) throw p.error("trailing data");
		return v;
	}

	public static String write(Object value) {
		StringBuilder sb = new StringBuilder();
		write(sb, value, 0);
		return sb.append('\n').toString();
	}

	private Object value() {
		ws();
		if (i >= s.length()) throw error("unexpected end");
		char c = s.charAt(i);
		switch (c) {
			case '{': return object();
			case '[': return array();
			case '"': return string();
			case 't': return literal("true", Boolean.TRUE);
			case 'f': return literal("false", Boolean.FALSE);
			case 'n': return literal("null", null);
			default:
				if (c == '-' || (c >= '0' && c <= '9')) return number();
				throw error("unexpected '" + c + "'");
		}
	}

	private Map<String, Object> object() {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		i++;
		ws();
		if (peek() == '}') {
			i++;
			return m;
		}
		while (true) {
			ws();
			if (peek() != '"') throw error("expected key");
			String k = string();
			ws();
			expect(':');
			m.put(k, value());
			ws();
			char c = next();
			if (c == '}') return m;
			if (c != ',') throw error("expected ',' or '}'");
		}
	}

	private List<Object> array() {
		List<Object> l = new ArrayList<Object>();
		i++;
		ws();
		if (peek() == ']') {
			i++;
			return l;
		}
		while (true) {
			l.add(value());
			ws();
			char c = next();
			if (c == ']') return l;
			if (c != ',') throw error("expected ',' or ']'");
		}
	}

	private String string() {
		i++;
		StringBuilder sb = new StringBuilder();
		while (true) {
			char c = next();
			if (c == '"') return sb.toString();
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			char e = next();
			switch (e) {
				case 'n': sb.append('\n'); break;
				case 't': sb.append('\t'); break;
				case 'r': sb.append('\r'); break;
				case 'b': sb.append('\b'); break;
				case 'f': sb.append('\f'); break;
				case 'u':
					if (i + 4 > s.length()) throw error("bad \\u escape");
					try {
						sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
					} catch (NumberFormatException ex) {
						throw error("bad \\u escape");
					}
					i += 4;
					break;
				default: sb.append(e);
			}
		}
	}

	private Double number() {
		int start = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
		try {
			return Double.valueOf(s.substring(start, i));
		} catch (NumberFormatException e) {
			throw error("bad number");
		}
	}

	private Object literal(String word, Object v) {
		if (!s.startsWith(word, i)) throw error("unexpected token");
		i += word.length();
		return v;
	}

	private void ws() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
	}

	private char peek() {
		return i < s.length() ? s.charAt(i) : 0;
	}

	private char next() {
		if (i >= s.length()) throw error("unexpected end");
		return s.charAt(i++);
	}

	private void expect(char c) {
		if (next() != c) throw error("expected '" + c + "'");
	}

	private IllegalArgumentException error(String msg) {
		return new IllegalArgumentException("JSON: " + msg + " at " + i);
	}

	private static void write(StringBuilder sb, Object v, int indent) {
		if (v == null) {
			sb.append("null");
		} else if (v instanceof String) {
			quote(sb, (String) v);
		} else if (v instanceof Boolean) {
			sb.append(v);
		} else if (v instanceof Number) {
			double d = ((Number) v).doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
			else if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
			else sb.append(d);
		} else if (v instanceof Map) {
			Map<?, ?> m = (Map<?, ?>) v;
			if (m.isEmpty()) {
				sb.append("{}");
				return;
			}
			sb.append("{\n");
			Iterator<? extends Map.Entry<?, ?>> it = m.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<?, ?> e = it.next();
				tabs(sb, indent + 1);
				quote(sb, String.valueOf(e.getKey()));
				sb.append(": ");
				write(sb, e.getValue(), indent + 1);
				sb.append(it.hasNext() ? ",\n" : "\n");
			}
			tabs(sb, indent);
			sb.append('}');
		} else if (v instanceof List) {
			List<?> l = (List<?>) v;
			sb.append('[');
			for (int k = 0; k < l.size(); k++) {
				if (k > 0) sb.append(", ");
				write(sb, l.get(k), indent);
			}
			sb.append(']');
		} else {
			quote(sb, v.toString());
		}
	}

	private static void tabs(StringBuilder sb, int n) {
		for (int k = 0; k < n; k++) sb.append('\t');
	}

	private static void quote(StringBuilder sb, String str) {
		sb.append('"');
		for (int k = 0; k < str.length(); k++) {
			char c = str.charAt(k);
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
			}
		}
		sb.append('"');
	}
}
