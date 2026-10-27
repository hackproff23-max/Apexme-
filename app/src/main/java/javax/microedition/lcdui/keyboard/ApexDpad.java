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
	private static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8;
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
	private int mask;
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
		float[] p = ApexPrefs.pos(POS, landscape(), 0.17f, 0.68f);
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

	public void paint(CanvasWrapper g, boolean opaque) {
		if (!enabled() || screen.isEmpty()) {
			return;
		}
		int alpha = opaque ? 0xFF : settings.vkAlpha;
		int a = alpha << 24;
		float cell = side / 3f;
		float gap = cell * 0.06f;
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				if (r == 1 && c == 1) {
					continue;
				}
				int m = maskOf(r, c);
				boolean on = pointer >= 0 && (mask & m) == m && m != 0 && mask == m;
				RectF cellRect = new RectF(rect.left + c * cell + gap, rect.top + r * cell + gap,
						rect.left + (c + 1) * cell - gap, rect.top + (r + 1) * cell - gap);
				g.setFillColor(a | (on ? settings.vkBgColorSelected : settings.vkBgColor));
				g.setDrawColor(a | settings.vkOutlineColor);
				g.setTextColor(a | (on ? settings.vkFgColorSelected : settings.vkFgColor));
				g.fillRoundRect(cellRect, (int) (cell * 0.2f), (int) (cell * 0.2f));
				g.drawRoundRect(cellRect, (int) (cell * 0.2f), (int) (cell * 0.2f));
				g.drawString(ARROWS[r * 3 + c], cellRect.centerX(), cellRect.centerY());
			}
		}
	}

	private static int maskOf(int row, int col) {
		int m = 0;
		if (row == 0) m |= UP;
		if (row == 2) m |= DOWN;
		if (col == 0) m |= LEFT;
		if (col == 2) m |= RIGHT;
		return m;
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
		setMask(0);
		invalidate();
	}

	private void update(float x, float y) {
		float cell = side / 3f;
		int col = Math.max(0, Math.min(2, (int) Math.floor((x - rect.left) / cell)));
		int row = Math.max(0, Math.min(2, (int) Math.floor((y - rect.top) / cell)));
		setMask(maskOf(row, col));
		invalidate();
	}

	private void setMask(int m) {
		if (m == mask) {
			return;
		}
		int old = mask;
		mask = m;
		if (target != null) {
			change(old, m, UP, Canvas.KEY_UP);
			change(old, m, DOWN, Canvas.KEY_DOWN);
			change(old, m, LEFT, Canvas.KEY_LEFT);
			change(old, m, RIGHT, Canvas.KEY_RIGHT);
		}
		handler.removeCallbacks(this);
		if (m != 0) {
			handler.postDelayed(this, 350);
		}
	}

	private void change(int old, int now, int bit, int key) {
		boolean was = (old & bit) != 0;
		boolean is = (now & bit) != 0;
		if (is && !was) {
			target.postKeyPressed(key);
		} else if (was && !is) {
			target.postKeyReleased(key);
		}
	}

	@Override
	public void run() {
		if (mask == 0 || target == null) {
			return;
		}
		if ((mask & UP) != 0) target.postKeyRepeated(Canvas.KEY_UP);
		if ((mask & DOWN) != 0) target.postKeyRepeated(Canvas.KEY_DOWN);
		if ((mask & LEFT) != 0) target.postKeyRepeated(Canvas.KEY_LEFT);
		if ((mask & RIGHT) != 0) target.postKeyRepeated(Canvas.KEY_RIGHT);
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
