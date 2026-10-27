package javax.microedition.lcdui.keyboard;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.graphics.CanvasWrapper;

import ru.playsoftware.j2meloader.apex.ApexPrefs;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** Analogico virtual: converte o movimento do dedo em teclas direcionais (8 direcoes). */
public class ApexAnalog implements Runnable {
	private static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8;
	private static final String POS = "analog";

	private final ProfileModel settings;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final RectF screen = new RectF();
	private final RectF rect = new RectF();
	private float cx, cy, radius;
	private float knobX, knobY;
	private int pointer = -1;
	private int mask;
	private int repeatCount;
	private boolean editing;
	private float offX, offY;
	private Canvas target;
	private View view;

	public ApexAnalog(ProfileModel settings) {
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
		radius = min * 0.13f * ApexPrefs.analogScale();
		float[] p = ApexPrefs.pos(POS, landscape(), 0.15f, 0.70f);
		place(scr.left + scr.width() * p[0], scr.top + scr.height() * p[1]);
	}

	private void place(float x, float y) {
		cx = Math.max(screen.left + radius, Math.min(screen.right - radius, x));
		cy = Math.max(screen.top + radius, Math.min(screen.bottom - radius, y));
		knobX = cx;
		knobY = cy;
		rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
	}

	private boolean enabled() {
		return ApexPrefs.analogEnabled();
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
		boolean active = pointer >= 0;
		Bitmap baseImg = ApexPrefs.buttonBitmap(ApexPrefs.ANALOG_BASE, active);
		if (baseImg != null) {
			g.drawBitmap(baseImg, rect, active ? 0xFF : Math.round(alpha * 0.7f));
		} else {
			g.setFillColor(a | settings.vkBgColor);
			g.setDrawColor(a | settings.vkOutlineColor);
			g.fillArc(rect, 0, 360);
			g.drawArc(rect, 0, 360);
		}
		float kr = radius * 0.45f;
		RectF knob = new RectF(knobX - kr, knobY - kr, knobX + kr, knobY + kr);
		Bitmap knobImg = ApexPrefs.buttonBitmap(ApexPrefs.ANALOG_KNOB, active);
		if (knobImg != null) {
			g.drawBitmap(knobImg, knob, active ? 0xFF : Math.round(alpha * 0.7f));
		} else {
			g.setFillColor(a | (active ? settings.vkBgColorSelected : settings.vkFgColor));
			g.setDrawColor(a | settings.vkOutlineColor);
			g.fillArc(knob, 0, 360);
			g.drawArc(knob, 0, 360);
		}
	}

	private boolean contains(float x, float y) {
		float dx = x - cx, dy = y - cy;
		float r = radius * 1.25f;
		return dx * dx + dy * dy <= r * r;
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
		pointer = -1;
		knobX = cx;
		knobY = cy;
		setMask(0);
		invalidate();
		return true;
	}

	public void cancel() {
		pointer = -1;
		knobX = cx;
		knobY = cy;
		setMask(0);
		invalidate();
	}

	private void update(float x, float y) {
		float dx = x - cx, dy = y - cy;
		float d = (float) Math.hypot(dx, dy);
		if (d > radius) {
			dx *= radius / d;
			dy *= radius / d;
			d = radius;
		}
		knobX = cx + dx;
		knobY = cy + dy;
		int m = 0;
		if (d > radius * 0.3f) {
			double ang = Math.toDegrees(Math.atan2(dy, dx)); // 0 = direita, 90 = baixo
			if (Math.abs(ang) < 67.5) m |= RIGHT;
			if (Math.abs(ang) > 112.5) m |= LEFT;
			if (ang > 22.5 && ang < 157.5) m |= DOWN;
			if (ang < -22.5 && ang > -157.5) m |= UP;
		}
		setMask(m);
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
		repeatCount = 0;
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
		repeatCount++;
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
