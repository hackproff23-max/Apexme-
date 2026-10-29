package ru.playsoftware.j2meloader.hex;

import java.util.Locale;

/** Funcoes de conversao compartilhadas pelo editor e pelo conversor. */
public final class HexUtil {
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	private HexUtil() {
	}

	/** "AB cd 0x1F,20" -> bytes. Devolve null se for invalido (digito solto ou caractere estranho). */
	public static byte[] parseHex(String s) {
		StringBuilder sb = new StringBuilder();
		String t = s.replace("0x", " ").replace("0X", " ");
		for (int i = 0; i < t.length(); i++) {
			char ch = t.charAt(i);
			if (Character.isWhitespace(ch) || ch == ',' || ch == ';' || ch == ':' || ch == '-') {
				continue;
			}
			if (Character.digit(ch, 16) < 0) {
				return null;
			}
			sb.append(ch);
		}
		if (sb.length() == 0 || sb.length() % 2 != 0) {
			return null;
		}
		byte[] out = new byte[sb.length() / 2];
		for (int i = 0; i < out.length; i++) {
			out[i] = (byte) ((Character.digit(sb.charAt(2 * i), 16) << 4) | Character.digit(sb.charAt(2 * i + 1), 16));
		}
		return out;
	}

	public static String toHex(byte[] b, String sep) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < b.length; i++) {
			if (i > 0) {
				sb.append(sep);
			}
			sb.append(HEX[(b[i] >> 4) & 15]).append(HEX[b[i] & 15]);
		}
		return sb.toString();
	}

	public static String hex(long v, int minDigits) {
		String s = Long.toHexString(v).toUpperCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder();
		for (int i = s.length(); i < minDigits; i++) {
			sb.append('0');
		}
		return sb.append(s).toString();
	}

	public static String bin8(int b) {
		String s = Integer.toBinaryString(b & 0xFF);
		return "00000000".substring(s.length()) + s;
	}
}
