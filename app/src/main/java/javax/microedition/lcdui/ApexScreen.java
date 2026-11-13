package javax.microedition.lcdui;

import javax.microedition.lcdui.event.CanvasEvent;

/** Aplica ao vivo resolucao e tela cheia no Canvas atual (janela Tela do Apexme). */
public final class ApexScreen {
	private ApexScreen() {
	}

	/** Le o estado de tela cheia do Canvas (campo privado). */
	public static boolean isFullscreen(Canvas c) {
		try {
			java.lang.reflect.Field f = Canvas.class.getDeclaredField("fullscreen");
			f.setAccessible(true);
			return f.getBoolean(c);
		} catch (Throwable e) {
			return false;
		}
	}

	/** w/h = 0 significa automatico. Deve rodar na thread principal. */
	public static void apply(Canvas c, int w, int h, boolean fullscreen) {
		Displayable.setVirtualSize(w, h);
		int w0 = c.width;
		int h0 = c.height;
		c.setFullScreenMode(fullscreen);
		c.updateSize();
		if (c.width != w0 || c.height != h0) {
			Display.postEvent(CanvasEvent.getInstance(c, CanvasEvent.SIZE_CHANGED, c.width, c.height));
		}
	}
}
