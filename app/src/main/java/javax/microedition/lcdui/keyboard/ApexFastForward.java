package javax.microedition.lcdui.keyboard;

import android.graphics.RectF;
import android.view.View;

import javax.microedition.lcdui.graphics.CanvasWrapper;
import javax.microedition.shell.ApexClock;

import ru.playsoftware.j2meloader.apex.ApexPrefs;
import ru.playsoftware.j2meloader.config.ProfileModel;

/**
 * Botao de fast-forward: toque para acelerar o jogo (velocidade escolhida no menu do teclado virtual)
 * e toque de novo para voltar ao normal. Pode ser arrastado no Modo edicao.
 */
public class ApexFastForward {
	private static final String POS = "ff";

	private final ProfileModel settings;
	private final RectF screen = new RectF();
	private final RectF rect = new RectF();
	private float cx, cy, w, h;
	private int pointer = -1;
	private boolean active;
	private boolean editing;
	private float offX, offY;
	private View view;

	public ApexFastForward(ProfileModel settings) {
		this.settings = settings;
		// jogo novo: sempre comeca na velocidade normal
		ApexClock.setSpeed(1);
	}

	public void setView(View v) {
		view = v;
	}

	private boolean enabled() {
		return ApexPrefs.ffEnabled();
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
		float size = Math.min(scr.width(), scr.height()) * 0.16f * ApexPrefs.buttonScale();
		w = size * 1.3f;
		h = size * 0.8f;
		float[] p = ApexPrefs.pos(POS, landscape(), 0.50f, 0.10f);
		place(scr.left + scr.width() * p[0], scr.top + scr.height() * p[1]);
	}

	private void place(float x, float y) {
		cx = Math.max(screen.left + w / 2f, Math.min(screen.right - w / 2f, x));
		cy = Math.max(screen.top + h / 2f, Math.min(screen.bottom - h / 2f, y));
		rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
	}

	/** Chamado quando o menu muda "ativar" ou a velocidade: aplica na hora se estiver acelerado. */
	public void applySettings() {
		if (active && !enabled()) {
			active = false;
		}
		ApexClock.setSpeed(active ? ApexPrefs.ffSpeed() : 1);
		invalidate();
	}

	private void toggle() {
		active = !active;
		ApexClock.setSpeed(active ? ApexPrefs.ffSpeed() : 1);
		invalidate();
	}

	public void paint(CanvasWrapper g, boolean opaque) {
		if (!enabled() || screen.isEmpty()) {
			return;
		}
		String label = ">> " + ApexPrefs.ffLabel(ApexPrefs.ffSpeed());
		ApexKeyPaint.draw(g, rect, ApexPrefs.FF, label, active || pointer >= 0, opaque, settings, 0.8f);
	}

	private boolean contains(float x, float y) {
		float pad = Math.min(w, h) * 0.12f;
		return x >= rect.left - pad && x <= rect.right + pad && y >= rect.top - pad && y <= rect.bottom + pad;
	}

	public boolean press(int id, float x, float y) {
		if (!enabled() || pointer >= 0 || !contains(x, y)) {
			return false;
		}
		pointer = id;
		invalidate();
		return true;
	}

	public boolean drag(int id, float x, float y) {
		return id == pointer;
	}

	public boolean release(int id, float x, float y) {
		if (id != pointer) {
			return false;
		}
		pointer = -1;
		if (contains(x, y)) {
			toggle();
		} else {
			invalidate();
		}
		return true;
	}

	public void cancel() {
		pointer = -1;
		invalidate();
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
		place(ApexPrefs.gridSnap(x - offX, screen.left), ApexPrefs.gridSnap(y - offY, screen.top));
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
