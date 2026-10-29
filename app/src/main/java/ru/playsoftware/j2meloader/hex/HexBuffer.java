package ru.playsoftware.j2meloader.hex;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

/** Conteudo do arquivo em memoria, com desfazer/refazer e marcacao dos bytes alterados. */
public final class HexBuffer {
	public static final int MAX_SIZE = 16 * 1024 * 1024;

	private static final class Op {
		int pos;
		byte[] removed;
		byte[] removedMarks;
		byte[] inserted;
	}

	private byte[] data = new byte[16];
	private byte[] marks = new byte[16];
	private int size;
	private final ArrayList<Op> undo = new ArrayList<>();
	private final ArrayList<Op> redo = new ArrayList<>();
	private int savedDepth;

	public void load(byte[] bytes) {
		data = Arrays.copyOf(bytes, Math.max(16, bytes.length));
		marks = new byte[data.length];
		size = bytes.length;
		undo.clear();
		redo.clear();
		savedDepth = 0;
	}

	public int size() {
		return size;
	}

	public int get(int i) {
		return data[i] & 0xFF;
	}

	public boolean isMarked(int i) {
		return marks[i] != 0;
	}

	public boolean isDirty() {
		return savedDepth != undo.size();
	}

	public void markSaved() {
		savedDepth = undo.size();
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	public void write(OutputStream out) throws IOException {
		out.write(data, 0, size);
	}

	public byte[] toArray() {
		return Arrays.copyOf(data, size);
	}

	public void set(int i, int value) {
		replace(i, 1, new byte[]{(byte) value});
	}

	public boolean insert(int pos, byte[] b) {
		return replace(pos, 0, b);
	}

	public void delete(int pos, int len) {
		replace(pos, Math.min(len, size - pos), new byte[0]);
	}

	/** Troca os bytes do trecho [pos, pos+b.length) pelos novos (cresce se passar do fim). */
	public boolean overwrite(int pos, byte[] b) {
		return replace(pos, Math.min(b.length, size - pos), b);
	}

	/** Remove removeLen bytes em pos e coloca ins no lugar. Devolve false se passar do limite. */
	public boolean replace(int pos, int removeLen, byte[] ins) {
		if (pos < 0 || removeLen < 0 || pos + removeLen > size) {
			return false;
		}
		if (size - removeLen + ins.length > MAX_SIZE) {
			return false;
		}
		Op op = new Op();
		op.pos = pos;
		op.removed = Arrays.copyOfRange(data, pos, pos + removeLen);
		op.removedMarks = Arrays.copyOfRange(marks, pos, pos + removeLen);
		op.inserted = ins.clone();
		applyRaw(pos, removeLen, ins, ones(ins.length));
		if (savedDepth > undo.size()) {
			savedDepth = -1;
		}
		undo.add(op);
		redo.clear();
		return true;
	}

	/** Desfaz a ultima alteracao; devolve a posicao afetada ou -1. */
	public int undo() {
		if (undo.isEmpty()) {
			return -1;
		}
		Op op = undo.remove(undo.size() - 1);
		applyRaw(op.pos, op.inserted.length, op.removed, op.removedMarks);
		redo.add(op);
		return op.pos;
	}

	public int redo() {
		if (redo.isEmpty()) {
			return -1;
		}
		Op op = redo.remove(redo.size() - 1);
		applyRaw(op.pos, op.removed.length, op.inserted, ones(op.inserted.length));
		undo.add(op);
		return op.pos;
	}

	/** Procura o padrao a partir de from (inclusive), dando a volta no arquivo. -1 se nao achar. */
	public int find(byte[] pat, int from) {
		if (pat.length == 0 || size < pat.length) {
			return -1;
		}
		int last = size - pat.length;
		if (from > last || from < 0) {
			from = 0;
		}
		for (int pass = 0; pass < 2; pass++) {
			int start = pass == 0 ? from : 0;
			int end = pass == 0 ? last : Math.min(last, from - 1);
			for (int i = start; i <= end; i++) {
				if (data[i] == pat[0]) {
					int j = 1;
					while (j < pat.length && data[i + j] == pat[j]) {
						j++;
					}
					if (j == pat.length) {
						return i;
					}
				}
			}
		}
		return -1;
	}

	private static byte[] ones(int n) {
		byte[] m = new byte[n];
		Arrays.fill(m, (byte) 1);
		return m;
	}

	private void applyRaw(int pos, int removeLen, byte[] ins, byte[] insMarks) {
		int newSize = size - removeLen + ins.length;
		if (newSize > data.length) {
			int cap = Math.max(newSize, data.length + (data.length >> 1));
			data = Arrays.copyOf(data, cap);
			marks = Arrays.copyOf(marks, cap);
		}
		int tail = size - pos - removeLen;
		System.arraycopy(data, pos + removeLen, data, pos + ins.length, tail);
		System.arraycopy(marks, pos + removeLen, marks, pos + ins.length, tail);
		System.arraycopy(ins, 0, data, pos, ins.length);
		System.arraycopy(insMarks, 0, marks, pos, ins.length);
		size = newSize;
	}
}
