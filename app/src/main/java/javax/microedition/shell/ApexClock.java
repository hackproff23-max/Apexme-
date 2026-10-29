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
		final float mult;

		State(long realBase, long virtualBase, float mult) {
			this.realBase = realBase;
			this.virtualBase = virtualBase;
			this.mult = mult;
		}
	}

	private static volatile State state = initial();

	/** true se o dex do jogo foi gerado com as chamadas ao ApexClock (instalado com suporte ao fast-forward). */
	public static volatile boolean gameSupported = true;
	/** Quantas vezes o jogo consultou o relogio (diagnostico; contagem aproximada). */
	public static long hits;

	/** Procura o nome da classe no dex do jogo: se nao houver, o jogo foi instalado antes do fast-forward. */
	public static void scanDex(java.io.File dex) {
		gameSupported = true;
		hits = 0;
		byte[] pat = "shell/ApexClock".getBytes();
		try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(dex), 64 * 1024)) {
			int matched = 0;
			int b;
			while ((b = in.read()) != -1) {
				if (b == (pat[matched] & 0xFF)) {
					if (++matched == pat.length) {
						return;
					}
				} else {
					matched = b == (pat[0] & 0xFF) ? 1 : 0;
				}
			}
			gameSupported = false;
		} catch (Throwable ignored) {
			gameSupported = true; // nao deu para ler: nao afirma nada
		}
	}

	private ApexClock() {
	}

	private static State initial() {
		long now = System.currentTimeMillis();
		return new State(now, now, 1f);
	}

	/** Muda a velocidade (1 = normal). O tempo virtual continua de onde estava. */
	public static synchronized void setSpeed(float mult) {
		if (mult < 1f) {
			mult = 1f;
		}
		State s = state;
		if (s.mult == mult) {
			return;
		}
		long real = System.currentTimeMillis();
		state = new State(real, s.virtualBase + (long) ((real - s.realBase) * (double) s.mult), mult);
	}

	public static float speed() {
		return state.mult;
	}

	/** Hora que o jogo enxerga (acelerada). */
	public static long currentTimeMillis() {
		hits++;
		State s = state;
		return s.virtualBase + (long) ((System.currentTimeMillis() - s.realBase) * (double) s.mult);
	}

	/** Encurta uma espera do jogo pela velocidade atual (minimo 1 ms para esperas positivas). */
	public static long scale(long ms) {
		float m = state.mult;
		if (m <= 1f || ms <= 0) {
			return ms;
		}
		return Math.max(1L, (long) (ms / m));
	}

	public static void sleep(long ms) throws InterruptedException {
		hits++;
		float m = state.mult;
		if (m <= 1f || ms <= 0) {
			Thread.sleep(ms);
		} else if (ms >= 1_000_000_000L) {
			Thread.sleep((long) (ms / m));
		} else {
			long nanos = (long) (ms * 1_000_000.0 / m);
			Thread.sleep(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
		}
	}

	/** Object.wait(ms) com a espera encurtada. Precisa ser chamado com o monitor do objeto, como o original. */
	public static void waitMillis(Object o, long ms) throws InterruptedException {
		hits++;
		float m = state.mult;
		if (m <= 1f || ms <= 0) {
			o.wait(ms);
		} else if (ms >= 1_000_000_000L) {
			o.wait((long) (ms / m));
		} else {
			long nanos = (long) (ms * 1_000_000.0 / m);
			o.wait(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
		}
	}
}
