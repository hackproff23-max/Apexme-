package javax.microedition.lcdui.keyboard;

import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.graphics.CanvasWrapper;

import ru.playsoftware.j2meloader.apex.ApexPrefs;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** D-pad de 8 direcoes (grade 3x3): no lugar das setas do teclado virtual. */
public class ApexDpad implements Runnable {
	private static final String POS = "dpad";
	private static final String[] ARROWS = {
			"↖", "↑", "↗",
			"←", "", "→",
			"↙", "↓", "↘"};

	private final ProfileModel settings;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final RectF screen = new RectF();
	private final RectF rect = new RectF();
	private float cx, cy, side;
	private int pointer = -1;
	private int cell = -1;
	private int[] activeKeys = new int[0];
	private boolean editing;
	private float offX, offY;
	private Canvas target;
	private View view;

	public ApexDpad(ProfileModel settings) {
		this.settings = settings;
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

	private boolean landscape() {
		return screen.width() > screen.height();
	}

	public void layout(RectF scr) {
		screen.set(scr);
		float min = Math.min(scr.width(), scr.height());
		side = min * 0.34f * ApexPrefs.dpadScale();
		float[] p = ApexPrefs.pos(POS, landscape(), 0.80f, 0.55f);
		place(scr.left + scr.width() * p[0], scr.top + scr.height() * p[1]);
	}

	private void place(float x, float y) {
		float h = side / 2f;
		cx = Math.max(screen.left + h, Math.min(screen.right - h, x));
		cy = Math.max(screen.top + h, Math.min(screen.bottom - h, y));
		rect.set(cx - h, cy - h, cx + h, cy + h);
	}

	private boolean enabled() {
		return ApexPrefs.dpadEnabled();
	}

	private void invalidate() {
		if (view != null) {
			view.postInvalidate();
		}
	}

	/** Indice 0..7 da direcao da casa (linha, coluna), ou -1 para o centro. */
	private static int dirIndex(int row, int col) {
		int i = row * 3 + col;
		if (i == 4) return -1;
		return i > 4 ? i - 1 : i;
	}

	private RectF cellRect(int row, int col) {
		float cell = side / 3f;
		return new RectF(rect.left + col * cell, rect.top + row * cell,
				rect.left + (col + 1) * cell, rect.top + (row + 1) * cell);
	}

	public void paint(CanvasWrapper g, boolean opaque) {
		if (!enabled() || screen.isEmpty()) {
			return;
		}
		android.graphics.Bitmap img = ApexPrefs.buttonBitmap(ApexPrefs.DPAD, false);
		if (img != null) {
			// uma imagem so; ao tocar, apenas a parte pressionada fica com a opacidade de "pressionado"
			RectF part = null;
			if (pointer >= 0 && cell >= 0) {
				int i = cell < 4 ? cell : cell + 1;
				part = cellRect(i / 3, i % 3);
			}
			g.drawBitmapHighlight(img, rect, Math.round(255 * ApexPrefs.normalOpacity()),
					Math.round(255 * ApexPrefs.pressedOpacity()), part);
			return;
		}
		int alpha = opaque ? 0xFF : settings.vkAlpha;
		int a = alpha << 24;
		float cellSize = side / 3f;
		float gap = cellSize * 0.06f;
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				int di = dirIndex(r, c);
				if (di < 0) {
					continue;
				}
				boolean on = pointer >= 0 && cell == di;
				RectF cr = cellRect(r, c);
				cr.inset(gap, gap);
				g.setFillColor(a | (on ? settings.vkBgColorSelected : settings.vkBgColor));
				g.setDrawColor(a | settings.vkOutlineColor);
				g.setTextColor(a | (on ? settings.vkFgColorSelected : settings.vkFgColor));
				g.fillRoundRect(cr, (int) (cellSize * 0.2f), (int) (cellSize * 0.2f));
				g.drawRoundRect(cr, (int) (cellSize * 0.2f), (int) (cellSize * 0.2f));
				g.drawString(ARROWS[r * 3 + c], cr.centerX(), cr.centerY());
			}
		}
	}

	private boolean contains(float x, float y) {
		float pad = side * 0.12f;
		return x >= rect.left - pad && x <= rect.right + pad && y >= rect.top - pad && y <= rect.bottom + pad;
	}

	public boolean press(int id, float x, float y) {
		if (!enabled() || pointer >= 0 || !contains(x, y)) {
			return false;
		}
		pointer = id;
		update(x, y);
		return true;
	}

	public boolean drag(int id, float x, float y) {
		if (id != pointer) {
			return false;
		}
		update(x, y);
		return true;
	}

	public boolean release(int id) {
		if (id != pointer) {
			return false;
		}
		cancel();
		return true;
	}

	public void cancel() {
		pointer = -1;
		setCell(-1);
		invalidate();
	}

	private void update(float x, float y) {
		float cs = side / 3f;
		int col = Math.max(0, Math.min(2, (int) Math.floor((x - rect.left) / cs)));
		int row = Math.max(0, Math.min(2, (int) Math.floor((y - rect.top) / cs)));
		setCell(dirIndex(row, col));
		invalidate();
	}

	/** Teclas da direcao: a tecla mapeada, ou (padrao) as setas; as diagonais mandam duas setas. */
	private static int[] keysFor(int dir) {
		int mapped = ApexPrefs.dpadMap()[dir];
		if (mapped != 0) {
			return new int[]{mapped};
		}
		int i = dir < 4 ? dir : dir + 1;
		int row = i / 3, col = i % 3;
		int[] tmp = new int[2];
		int n = 0;
		if (row == 0) tmp[n++] = Canvas.KEY_UP;
		if (row == 2) tmp[n++] = Canvas.KEY_DOWN;
		if (col == 0) tmp[n++] = Canvas.KEY_LEFT;
		if (col == 2) tmp[n++] = Canvas.KEY_RIGHT;
		return java.util.Arrays.copyOf(tmp, n);
	}

	private void setCell(int c) {
		if (c == cell) {
			return;
		}
		int old = cell;
		cell = c;
		if (target != null) {
			if (old >= 0) {
				for (int k : activeKeys) {
					target.postKeyReleased(k);
				}
			}
			if (c >= 0) {
				activeKeys = keysFor(c);
				for (int k : activeKeys) {
					target.postKeyPressed(k);
				}
			} else {
				activeKeys = new int[0];
			}
		}
		handler.removeCallbacks(this);
		if (c >= 0) {
			handler.postDelayed(this, 350);
		}
	}

	@Override
	public void run() {
		if (cell < 0 || target == null) {
			return;
		}
		for (int k : activeKeys) {
			target.postKeyRepeated(k);
		}
		handler.postDelayed(this, 90);
	}

	// ---- modo edicao ----
	public boolean editBegin(float x, float y) {
		if (!enabled() || !contains(x, y)) {
			return false;
		}
		editing = true;
		offX = x - cx;
		offY = y - cy;
		return true;
	}

	public boolean isEditing() {
		return editing;
	}

	public void editDrag(float x, float y) {
		place(x - offX, y - offY);
		invalidate();
	}

	public void editEnd() {
		editing = false;
		if (screen.width() > 0 && screen.height() > 0) {
			ApexPrefs.setPos(POS, landscape(), (cx - screen.left) / screen.width(),
					(cy - screen.top) / screen.height());
		}
	}
}
