package javax.microedition.shell;

/**
 * Relogio acelerado do fast-forward. Os jogos instalados passam a chamar estes metodos no lugar de
 * System.currentTimeMillis, Thread.sleep e Object.wait(long) (ver AndroidMethodVisitor). Com velocidade 1
 * tudo se comporta como antes. O tempo "virtual" nunca volta para tras ao desligar o fast-forward.
 */
public final class ApexClock {
	private static final class State {
		final long realBase;
		final long virtualBase;
		final int mult;

		State(long realBase, long virtualBase, int mult) {
			this.realBase = realBase;
			this.virtualBase = virtualBase;
			this.mult = mult;
		}
	}

	private static volatile State state = initial();

	private ApexClock() {
	}

	private static State initial() {
		long now = System.currentTimeMillis();
		return new State(now, now, 1);
	}

	/** Muda a velocidade (1 = normal). O tempo virtual continua de onde estava. */
	public static synchronized void setSpeed(int mult) {
		if (mult < 1) {
			mult = 1;
		}
		State s = state;
		if (s.mult == mult) {
			return;
		}
		long real = System.currentTimeMillis();
		state = new State(real, s.virtualBase + (real - s.realBase) * s.mult, mult);
	}

	public static int speed() {
		return state.mult;
	}

	/** Hora que o jogo enxerga (acelerada). */
	public static long currentTimeMillis() {
		State s = state;
		return s.virtualBase + (System.currentTimeMillis() - s.realBase) * s.mult;
	}

	/** Encurta uma espera do jogo pela velocidade atual (minimo 1 ms para esperas positivas). */
	public static long scale(long ms) {
		int m = state.mult;
		if (m <= 1 || ms <= 0) {
			return ms;
		}
		return Math.max(1L, ms / m);
	}

	public static void sleep(long ms) throws InterruptedException {
		int m = state.mult;
		if (m <= 1 || ms <= 0) {
			Thread.sleep(ms);
		} else if (ms >= 1_000_000_000L) {
			Thread.sleep(ms / m);
		} else {
			long nanos = ms * 1_000_000L / m;
			Thread.sleep(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
		}
	}

	/** Object.wait(ms) com a espera encurtada. Precisa ser chamado com o monitor do objeto, como o original. */
	public static void waitMillis(Object o, long ms) throws InterruptedException {
		int m = state.mult;
		if (m <= 1 || ms <= 0) {
			o.wait(ms);
		} else if (ms >= 1_000_000_000L) {
			o.wait(ms / m);
		} else {
			long nanos = ms * 1_000_000L / m;
			o.wait(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
		}
	}
}
