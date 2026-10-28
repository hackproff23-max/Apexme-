package ru.playsoftware.j2meloader.hex;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import ru.playsoftware.j2meloader.theme.ApexTheme;

/** Tela do editor: offset, bytes em hexadecimal e texto, com rolagem e selecao por toque. */
public class HexView extends View {
	public interface Listener {
		void onSelectionChanged(int index);
	}

	private static final int MARK_COLOR = 0xFFFFAA00;
	private static final int FIND_COLOR = 0x55FFFF00;

	private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint fill = new Paint();
	private final OverScroller scroller;
	private final GestureDetector gestures;
	private final float pad;
	private final float cw;
	private final float rowH;
	private final float baseOffset;
	private final int textColor;
	private final int offsetColor;
	private final int asciiColor;
	private final int bgColor;
	private final int selColor;

	private HexBuffer buf;
	private Listener listener;
	private int sel;
	private int hlStart = -1;
	private int hlLen;
	private int perRow = 8;
	private int digits = 6;
	private float hexX;
	private float asciiX;
	private float scrollY;
	private final char[] two = new char[2];

	public HexView(Context context) {
		super(context);
		float d = context.getResources().getDisplayMetrics().density;
		pad = 8 * d;
		text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
		text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13,
				context.getResources().getDisplayMetrics()));
		cw = text.measureText("0");
		Paint.FontMetrics fm = text.getFontMetrics();
		rowH = (fm.descent - fm.ascent) * 1.3f;
		baseOffset = (rowH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
		textColor = ApexTheme.textColor(context);
		offsetColor = ApexTheme.outlineColor(context);
		asciiColor = (textColor & 0x00FFFFFF) | 0xB0000000;
		bgColor = ApexTheme.bgColor(context) | 0xFF000000;
		selColor = (offsetColor & 0x00FFFFFF) | 0x88000000;
		scroller = new OverScroller(context);
		gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDown(MotionEvent e) {
				scroller.forceFinished(true);
				return true;
			}

			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				pick(e.getX(), e.getY());
				return true;
			}

			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
				scrollY = clampScroll(scrollY + dy);
				invalidate();
				return true;
			}

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
				scroller.fling(0, (int) scrollY, 0, (int) -vy, 0, 0, 0, (int) maxScroll());
				invalidate();
				return true;
			}
		});
		setFocusable(true);
	}

	public void setBuffer(HexBuffer b) {
		buf = b;
		sel = 0;
		hlStart = -1;
		scrollY = 0;
		relayout();
		invalidate();
	}

	public void setListener(Listener l) {
		listener = l;
	}

	public int getSelection() {
		return sel;
	}

	public int getBytesPerRow() {
		return perRow;
	}

	public void setSelection(int i) {
		if (buf == null || buf.size() == 0) {
			sel = 0;
		} else {
			sel = Math.max(0, Math.min(buf.size() - 1, i));
		}
		ensureVisible(sel);
		invalidate();
		if (listener != null) {
			listener.onSelectionChanged(sel);
		}
	}

	public void setHighlight(int start, int len) {
		hlStart = start;
		hlLen = len;
		invalidate();
	}

	/** Chamar depois de alterar o conteudo (tamanho pode ter mudado). */
	public void refresh() {
		relayout();
		if (buf != null && sel >= buf.size()) {
			sel = Math.max(0, buf.size() - 1);
		}
		invalidate();
	}

	private void ensureVisible(int index) {
		float top = pad + (index / perRow) * rowH;
		if (top < scrollY) {
			scrollY = clampScroll(top - rowH);
		} else if (top + rowH > scrollY + getHeight()) {
			scrollY = clampScroll(top + rowH * 2 - getHeight());
		}
	}

	private float contentHeight() {
		int size = buf == null ? 0 : buf.size();
		int rows = Math.max(1, (size + perRow - 1) / perRow);
		return pad * 2 + rows * rowH;
	}

	private float maxScroll() {
		return Math.max(0, contentHeight() - getHeight());
	}

	private float clampScroll(float v) {
		return Math.max(0, Math.min(maxScroll(), v));
	}

	private void relayout() {
		int size = buf == null ? 0 : buf.size();
		digits = size > 0xFFFFFF ? 8 : 6;
		int avail = (int) ((getWidth() - 2 * pad) / cw);
		int n = (avail - digits - 3) / 4;
		n = Math.max(4, Math.min(16, n / 4 * 4));
		perRow = n;
		hexX = pad + (digits + 2) * cw;
		asciiX = hexX + perRow * 3 * cw + cw;
		scrollY = clampScroll(scrollY);
	}

	@Override
	protected void onSizeChanged(int w, int h, int ow, int oh) {
		super.onSizeChanged(w, h, ow, oh);
		relayout();
	}

	@Override
	public void computeScroll() {
		if (scroller.computeScrollOffset()) {
			scrollY = clampScroll(scroller.getCurrY());
			postInvalidateOnAnimation();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		return gestures.onTouchEvent(e);
	}

	private void pick(float x, float y) {
		if (buf == null || buf.size() == 0) {
			return;
		}
		int row = (int) ((y + scrollY - pad) / rowH);
		if (row < 0) {
			row = 0;
		}
		int col;
		if (x >= asciiX - cw / 2) {
			col = (int) ((x - asciiX) / cw);
		} else {
			col = (int) ((x - hexX) / (3 * cw));
		}
		col = Math.max(0, Math.min(perRow - 1, col));
		setSelection(row * perRow + col);
	}

	@Override
	protected void onDraw(Canvas c) {
		c.drawColor(bgColor);
		if (buf == null) {
			return;
		}
		int size = buf.size();
		int rows = Math.max(1, (size + perRow - 1) / perRow);
		int first = Math.max(0, (int) ((scrollY - pad) / rowH));
		int last = Math.min(rows - 1, (int) ((scrollY + getHeight() - pad) / rowH));
		for (int r = first; r <= last; r++) {
			float top = pad + r * rowH - scrollY;
			float base = top + baseOffset;
			int start = r * perRow;
			text.setColor(offsetColor);
			c.drawText(hex(start, digits), pad, base, text);
			for (int col = 0; col < perRow; col++) {
				int i = start + col;
				if (i >= size) {
					break;
				}
				float hx = hexX + col * 3 * cw;
				float ax = asciiX + col * cw;
				if (hlStart >= 0 && i >= hlStart && i < hlStart + hlLen) {
					fill.setColor(FIND_COLOR);
					c.drawRect(hx, top, hx + 2 * cw, top + rowH, fill);
					c.drawRect(ax, top, ax + cw, top + rowH, fill);
				}
				if (i == sel) {
					fill.setColor(selColor);
					c.drawRect(hx - cw * 0.25f, top, hx + 2 * cw + cw * 0.25f, top + rowH, fill);
					c.drawRect(ax, top, ax + cw, top + rowH, fill);
				}
				int b = buf.get(i);
				two[0] = HEX[b >> 4];
				two[1] = HEX[b & 15];
				text.setColor(buf.isMarked(i) ? MARK_COLOR : textColor);
				c.drawText(two, 0, 2, hx, base, text);
				text.setColor(buf.isMarked(i) ? MARK_COLOR : asciiColor);
				two[0] = b >= 32 && b < 127 ? (char) b : '.';
				c.drawText(two, 0, 1, ax, base, text);
			}
		}
		if (size == 0) {
			text.setColor(asciiColor);
			c.drawText("(arquivo vazio)", pad, pad + baseOffset, text);
		}
	}

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	private static String hex(int v, int digits) {
		char[] s = new char[digits];
		for (int i = digits - 1; i >= 0; i--) {
			s[i] = HEX[v & 15];
			v >>>= 4;
		}
		return new String(s);
	}
}
