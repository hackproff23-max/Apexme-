package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.SparseIntArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import javax.microedition.util.ContextHolder;

/** Configuracoes extras do Apexme: shaders, analogico, teclado de letras, imagem por botao. */
public final class ApexPrefs {
	private static final String FILE = "apex_extras";
	public static final String SEL_AUTO = "";
	public static final String SEL_BUILTIN = "builtin";
	public static final String SEL_USER = "user:";

	private static SharedPreferences sp;
	private static SparseIntArray letterMap;

	private ApexPrefs() {
	}

	private static synchronized SharedPreferences sp() {
		if (sp == null) {
			sp = ContextHolder.getAppContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
		}
		return sp;
	}

	private static File dir(String sub) {
		File d = new File(ContextHolder.getAppContext().getFilesDir(), "apex/" + sub);
		//noinspection ResultOfMethodCallIgnored
		d.mkdirs();
		return d;
	}

	// ---------------- shaders ----------------
	public static String shaderSel() {
		return sp().getString("shader_sel", SEL_AUTO);
	}

	public static void setShaderSel(String s) {
		sp().edit().putString("shader_sel", s).apply();
	}

	/** Brilho: -0.5 .. 0.5 (padrao 0). */
	public static float brightness() {
		return sp().getFloat("brightness", 0f);
	}

	/** Contraste: 0 .. 2 (padrao 1). */
	public static float contrast() {
		return sp().getFloat("contrast", 1f);
	}

	/** Saturacao: 0 .. 2 (padrao 1). */
	public static float saturation() {
		return sp().getFloat("saturation", 1f);
	}

	/** Opacidade do efeito LCD: 0 .. 1 (padrao 0). */
	public static float lcdOpacity() {
		return sp().getFloat("lcd", 0f);
	}

	public static void setColorValues(float b, float c, float s, float lcd) {
		sp().edit().putFloat("brightness", b).putFloat("contrast", c)
				.putFloat("saturation", s).putFloat("lcd", lcd).apply();
	}

	public static void resetShaderValues() {
		setColorValues(0f, 1f, 1f, 0f);
	}

	public static File userShaderFile(String name) {
		return new File(dir("shaders"), name);
	}

	public static String[] listUserShaders() {
		String[] names = dir("shaders").list();
		if (names == null) {
			return new String[0];
		}
		Arrays.sort(names);
		return names;
	}

	/** Copia um fragment shader (.fsh/.frag/.glsl/.txt) para a pasta do app. Retorna o nome salvo. */
	public static String importShader(Context c, Uri uri) {
		String name = displayName(c, uri);
		if (name == null || name.isEmpty()) {
			name = "shader_" + System.currentTimeMillis() + ".fsh";
		}
		name = name.replaceAll("[^A-Za-z0-9._-]", "_");
		File out = userShaderFile(name);
		try (InputStream in = c.getContentResolver().openInputStream(uri);
			 OutputStream os = new FileOutputStream(out)) {
			if (in == null) {
				return null;
			}
			byte[] buf = new byte[8192];
			int n;
			long total = 0;
			while ((n = in.read(buf)) > 0) {
				total += n;
				if (total > 512 * 1024) {
					throw new java.io.IOException("too big");
				}
				os.write(buf, 0, n);
			}
		} catch (Exception e) {
			//noinspection ResultOfMethodCallIgnored
			out.delete();
			return null;
		}
		String text = ru.playsoftware.j2meloader.util.FileUtils.getText(out.getPath());
		if (!text.contains("main")) {
			//noinspection ResultOfMethodCallIgnored
			out.delete();
			return null;
		}
		return name;
	}

	private static String displayName(Context c, Uri uri) {
		try (Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
			if (cur != null && cur.moveToFirst()) {
				int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				if (i >= 0) {
					return cur.getString(i);
				}
			}
		} catch (Exception ignored) {
		}
		return uri.getLastPathSegment();
	}

	/** Liberar mover/aumentar os botoes A e B (padrao: desativado = ficam fixos). */
	public static boolean abEditable() {
		return sp().getBoolean("ab_editable", false);
	}

	public static void setAbEditable(boolean v) {
		sp().edit().putBoolean("ab_editable", v).apply();
	}

	/** Tamanho dos botoes A e B quando liberados: 0.5 .. 2 (padrao 1). */
	public static float abScale() {
		return sp().getFloat("ab_scale", 1f);
	}

	public static void setAbScale(float v) {
		sp().edit().putFloat("ab_scale", v).apply();
	}

	// ---------------- teclado virtual ----------------
	/** Escala global dos botoes: 0.5 .. 2 (padrao 1). */
	public static float buttonScale() {
		return sp().getFloat("btn_scale", 1f);
	}

	public static void setButtonScale(float v) {
		sp().edit().putFloat("btn_scale", v).apply();
	}

	public static boolean analogEnabled() {
		return sp().getBoolean("analog_on", true);
	}

	public static void setAnalogEnabled(boolean v) {
		sp().edit().putBoolean("analog_on", v).apply();
	}

	/** Escala do analogico: 0.5 .. 2 (padrao 1). */
	public static float analogScale() {
		return sp().getFloat("analog_scale", 1f);
	}

	public static void setAnalogScale(float v) {
		sp().edit().putFloat("analog_scale", v).apply();
	}

	public static boolean lettersEnabled() {
		return sp().getBoolean("letters_on", false);
	}

	public static void setLettersEnabled(boolean v) {
		sp().edit().putBoolean("letters_on", v).apply();
	}

	public static float[] pos(String name, boolean landscape, float defX, float defY) {
		String k = name + (landscape ? "_l" : "_p");
		return new float[]{sp().getFloat(k + "x", defX), sp().getFloat(k + "y", defY)};
	}

	public static void setPos(String name, boolean landscape, float x, float y) {
		String k = name + (landscape ? "_l" : "_p");
		sp().edit().putFloat(k + "x", x).putFloat(k + "y", y).apply();
	}

	// ---------------- mapeamento numero -> letra ----------------
	private static synchronized SparseIntArray numbers() {
		if (letterMap == null) {
			letterMap = new SparseIntArray();
			String s = sp().getString("number_map", "");
			for (String e : s.split(";")) {
				String[] kv = e.split(":");
				if (kv.length == 2) {
					try {
						letterMap.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return letterMap;
	}

	/** Letra (codigo minusculo) que a tecla numerica envia, ou -1. numberCode = '0'..'9', '*', '#'. */
	public static int numberTarget(int numberCode) {
		return numbers().get(numberCode, -1);
	}

	public static synchronized void setNumberMap(SparseIntArray map) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < map.size(); i++) {
			sb.append(map.keyAt(i)).append(':').append(map.valueAt(i)).append(';');
		}
		letterMap = map.clone();
		sp().edit().putString("number_map", sb.toString()).apply();
	}

	/** Se a tecla numerica tiver uma letra, devolve a letra; senao o proprio codigo. */
	public static int remapNumber(int code) {
		if ((code >= '0' && code <= '9') || code == '*' || code == '#') {
			return numbers().get(code, code);
		}
		return code;
	}

	// ---------------- imagens dos botoes (uma por botao) ----------------
	/** Ids dos botoes do teclado virtual, na mesma ordem do array keypad do VirtualKeyboard. */
	public static final String[] KEY_IDS = {
			"num1", "num2", "num3", "num4", "num5", "num6", "num7", "num8", "num9", "num0",
			"star", "pound", "softleft", "softright", "d", "c",
			"upleft", "up", "upright", "left", "right", "downleft", "down", "downright",
			"fire", "a", "b", "menu"};
	private static final String[] KEY_NAMES = {
			"Tecla 1", "Tecla 2", "Tecla 3", "Tecla 4", "Tecla 5", "Tecla 6", "Tecla 7", "Tecla 8",
			"Tecla 9", "Tecla 0", "Tecla *", "Tecla #", "Soft esquerda (L)", "Soft direita (R)",
			"Tecla D", "Tecla C", "Cima-esquerda", "Cima", "Cima-direita", "Esquerda", "Direita",
			"Baixo-esquerda", "Baixo", "Baixo-direita", "Fire (F)", "Tecla A", "Tecla B", "Menu (M)"};
	public static final String ANALOG_BASE = "analog_base";
	public static final String ANALOG_KNOB = "analog_knob";
	public static final String LETTER_PREFIX = "letter_";

	private static final HashMap<String, Bitmap> bitmaps = new HashMap<>();
	private static final HashSet<String> missing = new HashSet<>();

	/** Lista de todos os botoes personalizaveis: {id, nome}. */
	public static String[][] allButtons() {
		ArrayList<String[]> l = new ArrayList<>();
		for (int i = 0; i < KEY_IDS.length; i++) {
			l.add(new String[]{KEY_IDS[i], KEY_NAMES[i]});
		}
		l.add(new String[]{ANALOG_BASE, "Analógico (base)"});
		l.add(new String[]{ANALOG_KNOB, "Analógico (bolinha)"});
		for (char ch = 'a'; ch <= 'z'; ch++) {
			l.add(new String[]{LETTER_PREFIX + ch, "Letra " + Character.toUpperCase(ch)});
		}
		l.add(new String[]{LETTER_PREFIX + "back", "Letra: apagar"});
		l.add(new String[]{LETTER_PREFIX + "space", "Letra: espaço"});
		l.add(new String[]{LETTER_PREFIX + "ok", "Letra: OK"});
		return l.toArray(new String[0][]);
	}

	private static File imageFile(String id, boolean pressed) {
		return new File(dir("buttons"), id.replaceAll("[^A-Za-z0-9_]", "_")
				+ (pressed ? "_pressed.png" : "_normal.png"));
	}

	/**
	 * Imagem do botao {@code id}. Se estiver pressionado e nao houver imagem "pressionado",
	 * usa a normal. Devolve null se o botao nao tiver imagem (desenho padrao).
	 */
	public static synchronized Bitmap buttonBitmap(String id, boolean pressed) {
		if (id == null) {
			return null;
		}
		// uma imagem so: o estado pressionado e o proprio desenho, sem a transparencia do estado normal
		return load(id, false);
	}

	private static Bitmap load(String id, boolean pressed) {
		String k = id + (pressed ? "|p" : "|n");
		Bitmap b = bitmaps.get(k);
		if (b != null) {
			return b;
		}
		if (missing.contains(k)) {
			return null;
		}
		b = decode(imageFile(id, pressed));
		if (b == null) {
			missing.add(k);
		} else {
			bitmaps.put(k, b);
		}
		return b;
	}

	private static Bitmap decode(File f) {
		if (!f.exists()) {
			return null;
		}
		try {
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(f.getPath(), o);
			int sample = 1;
			while (o.outWidth / sample > 512 || o.outHeight / sample > 512) {
				sample *= 2;
			}
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = sample;
			return BitmapFactory.decodeFile(f.getPath(), o2);
		} catch (Throwable e) {
			return null;
		}
	}

	private static void invalidateCache() {
		bitmaps.clear();
		missing.clear();
	}

	public static synchronized boolean hasButtonImage(String id, boolean pressed) {
		return imageFile(id, pressed).exists();
	}

	/** Importa a imagem apenas para o botao {@code id}. */
	public static synchronized boolean importButtonImage(Context c, Uri uri, String id, boolean pressed) {
		File out = imageFile(id, pressed);
		File tmp = new File(out.getPath() + ".tmp");
		try (InputStream in = c.getContentResolver().openInputStream(uri);
			 OutputStream os = new FileOutputStream(tmp)) {
			if (in == null) {
				return false;
			}
			byte[] buf = new byte[8192];
			int n;
			long total = 0;
			while ((n = in.read(buf)) > 0) {
				total += n;
				if (total > 8 * 1024 * 1024) {
					throw new java.io.IOException("too big");
				}
				os.write(buf, 0, n);
			}
		} catch (Exception e) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return false;
		}
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(tmp.getPath(), o);
		if (o.outWidth <= 0 || o.outHeight <= 0) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return false;
		}
		//noinspection ResultOfMethodCallIgnored
		out.delete();
		boolean ok = tmp.renameTo(out);
		invalidateCache();
		return ok;
	}

	/** Remove as imagens (normal e pressionado) de um unico botao. */
	public static synchronized void clearButtonImage(String id) {
		//noinspection ResultOfMethodCallIgnored
		imageFile(id, false).delete();
		//noinspection ResultOfMethodCallIgnored
		imageFile(id, true).delete();
		invalidateCache();
	}

	/** Remove as imagens de todos os botoes. */
	public static synchronized void clearAllButtonImages() {
		File[] files = dir("buttons").listFiles();
		if (files != null) {
			for (File f : files) {
				//noinspection ResultOfMethodCallIgnored
				f.delete();
			}
		}
		invalidateCache();
	}
}
