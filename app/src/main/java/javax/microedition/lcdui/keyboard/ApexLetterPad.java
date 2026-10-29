package javax.microedition.lcdui.keyboard;

import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.graphics.CanvasWrapper;

import ru.playsoftware.j2meloader.apex.ApexPrefs;
import ru.playsoftware.j2meloader.config.ProfileModel;

/**
 * Teclado de letras proprio, para jogos em que os numeros nao funcionam.
 * Letras mapeadas (Teclado virtual > Mapear letras) enviam o numero correspondente.
 */
public class ApexLetterPad {
	private static final String POS = "letters";
	private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};

	private static class Key {
		final String id;
		final String label;
		final int code;
		final float units;
		final RectF rect = new RectF();
		boolean selected;
		int sentCode;

		Key(String id, String label, int code, float units) {
			this.id = ApexPrefs.LETTER_PREFIX + id;
			this.label = label;
			this.code = code;
			this.units = units;
		}
	}

	private final ProfileModel settings;
	private final ArrayList<Key> keys = new ArrayList<>();
	private final float[] rowOffset = new float[4];
	private final int[] rowOf;
	private final Key[] owned = new Key[10];
	private final RectF screen = new RectF();
	private final RectF bounds = new RectF();
	private float keySize;
	private boolean editing;
	private float offX, offY;
	private Canvas target;
	private View view;

	public ApexLetterPad(ProfileModel settings) {
		this.settings = settings;
		ArrayList<Integer> rows = new ArrayList<>();
		for (int r = 0; r < ROWS.length; r++) {
			for (char ch : ROWS[r].toCharArray()) {
				keys.add(new Key(String.valueOf(ch), String.valueOf(Character.toUpperCase(ch)), ch, 1f));
				rows.add(r);
			}
		}
		keys.add(new Key("back", "\u232B", Canvas.KEY_CLEAR, 1.5f));
		rows.add(3);
		keys.add(new Key("space", "ESP", ' ', 4f));
		rows.add(3);
		keys.add(new Key("ok", "OK", Canvas.KEY_FIRE, 1.5f));
		rows.add(3);
		rowOf = new int[rows.size()];
		for (int i = 0; i < rowOf.length; i++) {
			rowOf[i] = rows.get(i);
		}
		rowOffset[0] = 0f;
		rowOffset[1] = 0.5f;
		rowOffset[2] = 1.5f;
		rowOffset[3] = 1.5f;
	}

	public void setTarget(Canvas c) {
		target = c;
		if (c == null) {
			cancel();
		}
	}

	public void setView(View v) {
		view = v;
	}

	private boolean enabled() {
		return ApexPrefs.lettersEnabled();
	}

	private boolean landscape() {
		return screen.width() > screen.height();
	}

	private void invalidate() {
		if (view != null) {
			view.postInvalidate();
		}
	}

	public void layout(RectF scr) {
		screen.set(scr);
		keySize = Math.min(scr.width() / 14f, scr.height() / 10f) * 0.75f * ApexPrefs.buttonScale();
		float[] p = ApexPrefs.pos(POS, landscape(), 0.5f, 0.78f);
		float w = keySize * 10f;
		float h = keySize * 4f;
		moveTo(scr.left + scr.width() * p[0] - w / 2f, scr.top + scr.height() * p[1] - h / 2f);
	}

	private void moveTo(float left, float top) {
		float w = keySize * 10f;
		float h = keySize * 4f;
		left = Math.max(screen.left, Math.min(screen.right - w, left));
		top = Math.max(screen.top, Math.min(screen.bottom - h, top));
		bounds.set(left, top, left + w, top + h);
		float gap = keySize * 0.05f;
		float[] x = {left + rowOffset[0] * keySize, left + rowOffset[1] * keySize,
				left + rowOffset[2] * keySize, left + rowOffset[3] * keySize};
		for (int i = 0; i < keys.size(); i++) {
			Key k = keys.get(i);
			int r = rowOf[i];
			float kw = k.units * keySize;
			k.rect.set(x[r] + gap, top + r * keySize + gap, x[r] + kw - gap, top + (r + 1) * keySize - gap);
			x[r] += kw;
		}
	}

	public void paint(CanvasWrapper g, boolean opaque) {
		if (!enabled() || screen.isEmpty()) {
			return;
		}
		for (Key k : keys) {
			ApexKeyPaint.draw(g, k.rect, k.id, k.label, k.selected, opaque, settings, 0.8f);
		}
	}

	private int mapped(Key k) {
		return k.code;
	}

	public boolean press(int id, float x, float y) {
		if (!enabled() || id < 0 || id >= owned.length || !bounds.contains(x, y)) {
			return false;
		}
		for (Key k : keys) {
			if (k.rect.contains(x, y)) {
				if (owned[id] != null) {
					release(id);
				}
				owned[id] = k;
				k.selected = true;
				k.sentCode = mapped(k);
				if (target != null) {
					target.postKeyPressed(k.sentCode);
				}
				invalidate();
				return true;
			}
		}
		return false;
	}

	public boolean drag(int id, float x, float y) {
		if (id < 0 || id >= owned.length || owned[id] == null) {
			return false;
		}
		if (!owned[id].rect.contains(x, y)) {
			release(id);
			press(id, x, y);
		}
		return true;
	}

	public boolean release(int id) {
		if (id < 0 || id >= owned.length || owned[id] == null) {
			return false;
		}
		Key k = owned[id];
		owned[id] = null;
		k.selected = false;
		if (target != null) {
			target.postKeyReleased(k.sentCode);
		}
		invalidate();
		return true;
	}

	public void cancel() {
		for (int i = 0; i < owned.length; i++) {
			release(i);
		}
	}

	// ---- modo edicao ----
	public boolean editBegin(float x, float y) {
		if (!enabled() || !bounds.contains(x, y)) {
			return false;
		}
		editing = true;
		offX = x - bounds.left;
		offY = y - bounds.top;
		return true;
	}

	public boolean isEditing() {
		return editing;
	}

	public void editDrag(float x, float y) {
		float hw = bounds.width() / 2f, hh = bounds.height() / 2f;
		moveTo(ApexPrefs.gridSnap(x - offX + hw, screen.left) - hw,
				ApexPrefs.gridSnap(y - offY + hh, screen.top) - hh);
		invalidate();
	}

	public void editEnd() {
		editing = false;
		if (screen.width() > 0 && screen.height() > 0) {
			ApexPrefs.setPos(POS, landscape(), (bounds.centerX() - screen.left) / screen.width(),
					(bounds.centerY() - screen.top) / screen.height());
		}
	}
}
