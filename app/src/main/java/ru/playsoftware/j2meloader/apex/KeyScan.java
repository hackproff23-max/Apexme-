package ru.playsoftware.j2meloader.apex;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Detecta se um jogo J2ME usa letras (Q, W, A, S...) ou numeros (0-9, *, #) nas teclas.
 * Le os .class do jar original, acha as classes que tratam teclas (keyPressed/keyReleased/
 * keyRepeated ou que estendem Canvas/GameCanvas) e conta as constantes comparadas com o codigo
 * da tecla: if_icmpeq/if_icmpne, lookupswitch e tableswitch. E uma heuristica: pode errar.
 */
public final class KeyScan {
	public static final int UNKNOWN = 0;
	public static final int NUMBERS = 1;
	public static final int LETTERS = 2;

	/** Minimo de letras diferentes para considerar que o jogo usa letras. */
	private static final int MIN_LETTERS = 2;
	private static final int MAX_CLASS_SIZE = 600 * 1024;

	private KeyScan() {
	}

	/** Resultado: tipo + contagens, para log/depuracao. */
	public static final class Result {
		public int mode = UNKNOWN;
		public int letters;
		public int digits;
		/** Letras que o jogo realmente compara (na caixa em que aparecem), ex.: "qwas". */
		public String letterChars = "";

		@Override
		public String toString() {
			return "mode=" + mode + " letters=" + letters + " digits=" + digits;
		}
	}

	public static Result scan(File jar) {
		Result r = new Result();
		java.util.TreeMap<Integer, Integer> letters = new java.util.TreeMap<>();
		HashSet<Integer> digits = new HashSet<>();
		try (ZipFile zip = new ZipFile(jar)) {
			Enumeration<? extends ZipEntry> en = zip.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				if (e.isDirectory() || !e.getName().endsWith(".class") || e.getSize() > MAX_CLASS_SIZE) {
					continue;
				}
				try (InputStream in = new BufferedInputStream(zip.getInputStream(e))) {
					scanClass(new DataInputStream(in), letters, digits);
				} catch (Throwable ignored) {
					// classe estranha/ofuscada: ignora e segue
				}
			}
		} catch (IOException e) {
			return r;
		}
		r.letters = letters.size();
		r.digits = digits.size();
		StringBuilder sb = new StringBuilder();
		for (int code : letters.values()) {
			sb.append((char) code);
		}
		r.letterChars = sb.toString();
		if (r.letters >= MIN_LETTERS && r.letters > r.digits) {
			r.mode = LETTERS;
		} else if (r.letters + r.digits > 0) {
			r.mode = NUMBERS;
		}
		return r;
	}

	private static void addKey(int c, java.util.TreeMap<Integer, Integer> letters, HashSet<Integer> digits) {
		if (c >= 'a' && c <= 'z') {
			letters.put(c, c);
		} else if (c >= 'A' && c <= 'Z') {
			if (!letters.containsKey(c + 32)) {
				letters.put(c + 32, c);
			}
		} else if ((c >= '0' && c <= '9') || c == '*' || c == '#') {
			digits.add(c);
		}
	}

	private static void scanClass(DataInputStream in, java.util.TreeMap<Integer, Integer> letters, HashSet<Integer> digits)
			throws IOException {
		if (in.readInt() != 0xCAFEBABE) {
			return;
		}
		in.readUnsignedShort();
		in.readUnsignedShort();
		int cpCount = in.readUnsignedShort();
		String[] utf = new String[cpCount];
		int[] classNameIdx = new int[cpCount];
		int[] ints = new int[cpCount];
		boolean[] isInt = new boolean[cpCount];
		for (int i = 1; i < cpCount; i++) {
			int tag = in.readUnsignedByte();
			switch (tag) {
				case 1:
					utf[i] = in.readUTF();
					break;
				case 3:
					ints[i] = in.readInt();
					isInt[i] = true;
					break;
				case 4:
					in.readInt();
					break;
				case 5:
				case 6:
					in.readLong();
					i++;
					break;
				case 7:
					classNameIdx[i] = in.readUnsignedShort();
					break;
				case 8:
				case 16:
				case 19:
				case 20:
					in.readUnsignedShort();
					break;
				case 9:
				case 10:
				case 11:
				case 12:
				case 17:
				case 18:
					in.readInt();
					break;
				case 15:
					in.readUnsignedByte();
					in.readUnsignedShort();
					break;
				default:
					return;
			}
		}
		in.readUnsignedShort();
		in.readUnsignedShort();
		int superIdx = in.readUnsignedShort();
		String superName = superIdx > 0 && superIdx < cpCount ? utf[classNameIdx[superIdx]] : null;
		int ifaces = in.readUnsignedShort();
		for (int i = 0; i < ifaces; i++) {
			in.readUnsignedShort();
		}
		int fields = in.readUnsignedShort();
		for (int i = 0; i < fields; i++) {
			in.readUnsignedShort();
			in.readUnsignedShort();
			in.readUnsignedShort();
			skipAttrs(in);
		}
		int methods = in.readUnsignedShort();
		byte[][] codes = new byte[methods][];
		boolean[] keyMethod = new boolean[methods];
		boolean[] helper = new boolean[methods];
		boolean keyClass = superName != null
				&& (superName.endsWith("/Canvas") || superName.endsWith("/GameCanvas"));
		for (int i = 0; i < methods; i++) {
			in.readUnsignedShort();
			String name = utf[in.readUnsignedShort()];
			String desc = utf[in.readUnsignedShort()];
			if ("(I)V".equals(desc) && name != null
					&& (name.equals("keyPressed") || name.equals("keyReleased") || name.equals("keyRepeated"))) {
				keyClass = true;
				keyMethod[i] = true;
			} else if (desc != null && desc.startsWith("(I)") && !"<init>".equals(name)) {
				helper[i] = true; // possivel auxiliar que recebe o codigo da tecla
			}
			int attrs = in.readUnsignedShort();
			for (int a = 0; a < attrs; a++) {
				String an = utf[in.readUnsignedShort()];
				int len = in.readInt();
				if ("Code".equals(an)) {
					in.readUnsignedShort();
					in.readUnsignedShort();
					int codeLen = in.readInt();
					byte[] code = new byte[codeLen];
					in.readFully(code);
					codes[i] = code;
					int rest = len - 8 - codeLen;
					while (rest > 0) {
						int s = (int) in.skip(rest);
						if (s <= 0) {
							in.readByte();
							s = 1;
						}
						rest -= s;
					}
				} else {
					skipBytes(in, len);
				}
			}
		}
		if (!keyClass) {
			return;
		}
		boolean anyKeyMethod = false;
		for (boolean b : keyMethod) {
			anyKeyMethod |= b;
		}
		if (!anyKeyMethod) {
			return; // so Canvas sem keyPressed: paint/logica geram falsos positivos
		}
		for (int i = 0; i < codes.length; i++) {
			if (codes[i] != null && (keyMethod[i] || helper[i])) {
				scanCode(codes[i], ints, isInt, letters, digits);
			}
		}
	}

	private static void skipAttrs(DataInputStream in) throws IOException {
		int n = in.readUnsignedShort();
		for (int i = 0; i < n; i++) {
			in.readUnsignedShort();
			skipBytes(in, in.readInt());
		}
	}

	private static void skipBytes(DataInputStream in, int n) throws IOException {
		while (n > 0) {
			int s = (int) in.skip(n);
			if (s <= 0) {
				in.readByte();
				s = 1;
			}
			n -= s;
		}
	}

	private static int u1(byte[] c, int i) {
		return c[i] & 0xff;
	}

	private static int s2(byte[] c, int i) {
		return (short) (((c[i] & 0xff) << 8) | (c[i + 1] & 0xff));
	}

	private static int s4(byte[] c, int i) {
		return ((c[i] & 0xff) << 24) | ((c[i + 1] & 0xff) << 16) | ((c[i + 2] & 0xff) << 8) | (c[i + 3] & 0xff);
	}

	/** Valor da constante inteira empurrada pela instrucao em pc, ou Integer.MIN_VALUE. */
	private static int constAt(byte[] c, int pc, int[] ints, boolean[] isInt) {
		int op = u1(c, pc);
		if (op >= 3 && op <= 8) {
			return op - 3;
		}
		if (op == 16) {
			return c[pc + 1];
		}
		if (op == 17) {
			return s2(c, pc + 1);
		}
		if (op == 18) {
			int idx = u1(c, pc + 1);
			return idx < isInt.length && isInt[idx] ? ints[idx] : Integer.MIN_VALUE;
		}
		if (op == 19) {
			int idx = u1(c, pc + 1) << 8 | u1(c, pc + 2);
			return idx < isInt.length && isInt[idx] ? ints[idx] : Integer.MIN_VALUE;
		}
		return Integer.MIN_VALUE;
	}

	private static boolean isLoad(int op) {
		return (op >= 21 && op <= 25) || (op >= 26 && op <= 45);
	}

	private static int len(byte[] c, int pc) {
		int op = u1(c, pc);
		if (op == 16 || op == 18 || (op >= 21 && op <= 25) || (op >= 54 && op <= 58)
				|| op == 169 || op == 188) {
			return 2;
		}
		if (op == 17 || op == 19 || op == 20 || op == 132 || (op >= 153 && op <= 168)
				|| (op >= 178 && op <= 184) || op == 187 || op == 189 || op == 192 || op == 193
				|| op == 198 || op == 199) {
			return 3;
		}
		if (op == 185 || op == 186 || op == 200 || op == 201) {
			return 5;
		}
		if (op == 197) {
			return 4;
		}
		if (op == 196) {
			return u1(c, pc + 1) == 132 ? 6 : 4;
		}
		if (op == 170) {
			int p = (pc + 4) & ~3;
			int lo = s4(c, p + 4);
			int hi = s4(c, p + 8);
			return p + 12 + (hi - lo + 1) * 4 - pc;
		}
		if (op == 171) {
			int p = (pc + 4) & ~3;
			int n = s4(c, p + 4);
			return p + 8 + n * 8 - pc;
		}
		return 1;
	}

	private static void scanCode(byte[] c, int[] ints, boolean[] isInt,
								 java.util.TreeMap<Integer, Integer> letters, HashSet<Integer> digits) {
		int pc = 0;
		int prev = -1;   // pc da instrucao anterior
		int prev2 = -1;  // pc da anterior a anterior
		while (pc < c.length) {
			int op = u1(c, pc);
			int l = len(c, pc);
			if (l <= 0 || pc + l > c.length) {
				return;
			}
			if (op == 159 || op == 160) { // if_icmpeq / if_icmpne
				if (prev >= 0) {
					int v = constAt(c, prev, ints, isInt);
					if (v != Integer.MIN_VALUE) {
						addKey(v, letters, digits);
					} else if (prev2 >= 0 && isLoad(u1(c, prev))) {
						v = constAt(c, prev2, ints, isInt);
						if (v != Integer.MIN_VALUE) {
							addKey(v, letters, digits);
						}
					}
				}
			} else if (op == 171) { // lookupswitch
				int p = (pc + 4) & ~3;
				int n = s4(c, p + 4);
				for (int i = 0; i < n; i++) {
					addKey(s4(c, p + 8 + i * 8), letters, digits);
				}
			} else if (op == 170) { // tableswitch
				int p = (pc + 4) & ~3;
				int def = s4(c, p);
				int lo = s4(c, p + 4);
				int hi = s4(c, p + 8);
				if (hi - lo < 256 && p + 12 + (hi - lo + 1) * 4 <= c.length) {
					for (int k = lo; k <= hi; k++) {
						if (s4(c, p + 12 + (k - lo) * 4) != def) {
							addKey(k, letters, digits);
						}
					}
				}
			}
			prev2 = prev;
			prev = pc;
			pc += l;
		}
	}
}
