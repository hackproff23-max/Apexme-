package ru.playsoftware.j2meloader.apex;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.SparseIntArray;

import org.json.JSONException;
import org.json.JSONObject;

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

	/** Tipos de jogo: cada um tem sua propria pasta de imagens em importacao/botoes/TIPO/. */
	public static final String GAME_PADRAO = "padrao";
	public static final String GAME_TIRO = "tiro";
	public static final String GAME_FUTEBOL = "futebol";
	public static final String[] GAME_TYPES = {GAME_PADRAO, GAME_TIRO, GAME_FUTEBOL};
	public static final String[] GAME_NAMES = {"Padrão", "Tiro", "Futebol"};

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

	// ---------------- tipo de jogo ----------------
	/** Tipo de jogo ativo (padrao, tiro ou futebol). */
	public static String gameType() {
		String t = sp().getString("game_type", GAME_PADRAO);
		for (String k : GAME_TYPES) {
			if (k.equals(t)) {
				return t;
			}
		}
		return GAME_PADRAO;
	}

	public static String gameTypeName() {
		String t = gameType();
		for (int i = 0; i < GAME_TYPES.length; i++) {
			if (GAME_TYPES[i].equals(t)) {
				return GAME_NAMES[i];
			}
		}
		return GAME_NAMES[0];
	}

	public static synchronized void setGameType(String type) {
		for (String k : GAME_TYPES) {
			if (k.equals(type)) {
				sp().edit().putString("game_type", type).apply();
				invalidateCache();
				return;
			}
		}
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
		return new File(ImportStore.dir(ImportStore.SHADERS), name);
	}

	public static String[] listUserShaders() {
		String[] names = ImportStore.dir(ImportStore.SHADERS).list();
		if (names == null) {
			return new String[0];
		}
		Arrays.sort(names);
		return names;
	}


	/** Shaders que deram erro de compilacao/link neste aparelho (guardados para nao oferecer de novo). */
	public static synchronized java.util.Set<String> badShaders() {
		return new HashSet<>(sp().getStringSet("bad_shaders", new HashSet<String>()));
	}

	public static synchronized void markShaderBad(String name) {
		java.util.Set<String> s = badShaders();
		if (s.add(name)) {
			sp().edit().putStringSet("bad_shaders", s).apply();
		}
	}

	public static synchronized void clearBadShaders() {
		sp().edit().remove("bad_shaders").apply();
	}

	/** Lista so os shaders que ainda nao falharam. */
	public static String[] listWorkingShaders() {
		java.util.Set<String> bad = badShaders();
		ArrayList<String> ok = new ArrayList<>();
		for (String n : listUserShaders()) {
			if (!bad.contains(n)) {
				ok.add(n);
			}
		}
		return ok.toArray(new String[0]);
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
		ImportStore.sync();
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

	/** D-pad de 8 direcoes no lugar das setas (padrao: ligado). */
	public static boolean dpadEnabled() {
		return sp().getBoolean("dpad_on", false);
	}

	public static void setDpadEnabled(boolean v) {
		sp().edit().putBoolean("dpad_on", v).apply();
	}

	public static float dpadScale() {
		return sp().getFloat("dpad_scale", 1f);
	}

	public static void setDpadScale(float v) {
		sp().edit().putFloat("dpad_scale", v).apply();
	}

	/** Teclas enviadas por cada direcao do D-pad (cima-esq, cima, cima-dir, esq, dir, baixo-esq, baixo, baixo-dir); 0 = padrao. */
	public static int[] dpadMap() {
		int[] m = new int[8];
		String s = sp().getString("dpad_map", "");
		if (s != null && !s.isEmpty()) {
			String[] p = s.split(",");
			for (int i = 0; i < 8 && i < p.length; i++) {
				try {
					m[i] = Integer.parseInt(p[i].trim());
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return m;
	}

	public static void setDpadMap(int[] m) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			if (i > 0) sb.append(',');
			sb.append(m[i]);
		}
		sp().edit().putString("dpad_map", sb.toString()).apply();
	}

	// ---------------- fast-forward ----------------
	public static final float[] FF_SPEEDS = {1f, 1.25f, 1.5f, 2f, 3f, 4f, 6f, 8f, 16f, 32f};
	public static final String FF = "fastforward";

	/** Botao de fast-forward na tela (padrao: desativado). */
	public static boolean ffEnabled() {
		return sp().getBoolean("ff_on", false);
	}

	public static void setFfEnabled(boolean v) {
		sp().edit().putBoolean("ff_on", v).apply();
	}

	/** Velocidade do fast-forward: 1, 1.25, 1.5, 2, 3, 4, 6, 8, 16 ou 32 (padrao 2). */
	public static float ffSpeed() {
		float s = sp().getFloat("ff_speed_f", sp().getInt("ff_speed", 2));
		for (float k : FF_SPEEDS) {
			if (Math.abs(k - s) < 0.001f) {
				return k;
			}
		}
		return 2f;
	}

	public static void setFfSpeed(float v) {
		for (float k : FF_SPEEDS) {
			if (Math.abs(k - v) < 0.001f) {
				sp().edit().putFloat("ff_speed_f", k).apply();
				return;
			}
		}
	}

	/** Texto da velocidade: "2x", "1.25x", "1.5x". */
	public static String ffLabel(float v) {
		return (v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v)) + "x";
	}

	/** Opacidade da imagem do botao normal: 0 .. 1 (padrao 0.7). */
	public static float normalOpacity() {
		return sp().getFloat("op_normal", 0.7f);
	}

	public static void setNormalOpacity(float v) {
		sp().edit().putFloat("op_normal", v).apply();
	}

	/** Opacidade da imagem do botao pressionado: 0 .. 1 (padrao 1). */
	public static float pressedOpacity() {
		return sp().getFloat("op_pressed", 1f);
	}

	public static void setPressedOpacity(float v) {
		sp().edit().putFloat("op_pressed", v).apply();
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

	/** Analogico (padrao: ligado). */
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

	// ---------------- grade do editor de teclado ----------------
	/** Grade ligada no modo mover (padrao: ligada). */
	public static boolean gridOn() {
		return sp().getBoolean("grid_on", true);
	}

	public static void setGridOn(boolean v) {
		sp().edit().putBoolean("grid_on", v).apply();
	}

	/** Tamanho da casa da grade (4..64, padrao 8). */
	public static int gridSize() {
		return Math.max(4, Math.min(64, sp().getInt("grid_size", 8)));
	}

	public static void setGridSize(int v) {
		sp().edit().putInt("grid_size", Math.max(4, Math.min(64, v))).apply();
	}

	/** Tamanho da casa em pixels. */
	public static float gridCell() {
		float d = ContextHolder.getAppContext().getResources().getDisplayMetrics().density;
		return Math.max(2f, gridSize() * d);
	}

	/** Alinha um valor a grade (a partir de origin); sem grade devolve o proprio valor. */
	public static float gridSnap(float v, float origin) {
		if (!gridOn()) {
			return v;
		}
		float c = gridCell();
		return origin + Math.round((v - origin) / c) * c;
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
	public static final String DPAD = "dpad";
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
		l.add(new String[]{DPAD, "D-pad (8 direções)"});
		l.add(new String[]{FF, "Fast-forward"});
		for (char ch = 'a'; ch <= 'z'; ch++) {
			l.add(new String[]{LETTER_PREFIX + ch, "Letra " + Character.toUpperCase(ch)});
		}
		l.add(new String[]{LETTER_PREFIX + "back", "Letra: apagar"});
		l.add(new String[]{LETTER_PREFIX + "space", "Letra: espaço"});
		l.add(new String[]{LETTER_PREFIX + "ok", "Letra: OK"});
		return l.toArray(new String[0][]);
	}

	private static String imageName(String id, boolean pressed) {
		return id.replaceAll("[^A-Za-z0-9_]", "_") + (pressed ? "_pressed.png" : "_normal.png");
	}

	private static File imageFile(String type, String id, boolean pressed) {
		return new File(ImportStore.dir(ImportStore.botoesSub(type)), imageName(id, pressed));
	}

	/** Arquivo da imagem do botao no tipo de jogo ativo. */
	private static File imageFile(String id, boolean pressed) {
		return imageFile(gameType(), id, pressed);
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
		String type = gameType();
		String k = type + "|" + id + (pressed ? "|p" : "|n");
		Bitmap b = bitmaps.get(k);
		if (b != null) {
			return b;
		}
		if (missing.contains(k)) {
			return null;
		}
		b = decode(imageFile(type, id, pressed));
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
		ImportStore.sync();
		return ok;
	}

	/** Remove as imagens (normal e pressionado) de um unico botao. */
	public static synchronized void clearButtonImage(String id) {
		//noinspection ResultOfMethodCallIgnored
		imageFile(id, false).delete();
		//noinspection ResultOfMethodCallIgnored
		imageFile(id, true).delete();
		invalidateCache();
		ImportStore.sync();
	}

	/** Remove as imagens de todos os botoes do tipo de jogo ativo. */
	public static synchronized void clearAllButtonImages() {
		File[] files = ImportStore.dir(ImportStore.botoesSub(gameType())).listFiles();
		if (files != null) {
			for (File f : files) {
				String n = f.getName();
				if (n.endsWith(".png") || n.endsWith(".tmp")) {
					//noinspection ResultOfMethodCallIgnored
					f.delete();
				}
			}
		}
		invalidateCache();
		ImportStore.sync();
	}
	// ---------------- perfil: analogico e D-pad ----------------
	/** Imagens do analogico e do D-pad que vao junto com o perfil. */
	private static final String[] PROFILE_IMG_IDS = {ANALOG_BASE, ANALOG_KNOB, DPAD, FF};
	private static final String[] PROFILE_POS_NAMES = {"analog", "dpad", "ff"};

	/** Valores do analogico e do D-pad (tamanho, opacidade, mapeamento, posicao, tipo) para gravar no perfil. */
	public static synchronized JSONObject exportExtras() throws JSONException {
		JSONObject o = new JSONObject();
		o.put("versao", 1);
		o.put("tipo_de_jogo", gameType());
		o.put("analog_ligado", analogEnabled());
		o.put("analog_tamanho", analogScale());
		o.put("dpad_ligado", dpadEnabled());
		o.put("dpad_tamanho", dpadScale());
		o.put("dpad_mapa", sp().getString("dpad_map", ""));
		o.put("ff_ligado", ffEnabled());
		o.put("ff_velocidade", (double) ffSpeed());
		o.put("opacidade_normal", normalOpacity());
		o.put("opacidade_pressionado", pressedOpacity());
		JSONObject pos = new JSONObject();
		for (String n : PROFILE_POS_NAMES) {
			for (String o2 : new String[]{"_p", "_l"}) {
				for (String ax : new String[]{"x", "y"}) {
					String k = n + o2 + ax;
					if (sp().contains(k)) {
						pos.put(k, (double) sp().getFloat(k, 0f));
					}
				}
			}
		}
		o.put("posicao", pos);
		return o;
	}

	/** Aplica os valores gravados no perfil. O que nao estiver no arquivo fica como esta. */
	public static synchronized void importExtras(JSONObject o) {
		SharedPreferences.Editor e = sp().edit();
		String type = o.optString("tipo_de_jogo", "");
		for (String k : GAME_TYPES) {
			if (k.equals(type)) {
				e.putString("game_type", type);
			}
		}
		if (o.has("analog_ligado")) e.putBoolean("analog_on", o.optBoolean("analog_ligado"));
		if (o.has("analog_tamanho")) e.putFloat("analog_scale", (float) o.optDouble("analog_tamanho", 1));
		if (o.has("dpad_ligado")) e.putBoolean("dpad_on", o.optBoolean("dpad_ligado"));
		if (o.has("dpad_tamanho")) e.putFloat("dpad_scale", (float) o.optDouble("dpad_tamanho", 1));
		if (o.has("dpad_mapa")) e.putString("dpad_map", o.optString("dpad_mapa", ""));
		if (o.has("ff_ligado")) e.putBoolean("ff_on", o.optBoolean("ff_ligado"));
		if (o.has("ff_velocidade")) e.putFloat("ff_speed_f", (float) o.optDouble("ff_velocidade", 2));
		if (o.has("opacidade_normal")) e.putFloat("op_normal", (float) o.optDouble("opacidade_normal", 0.7));
		if (o.has("opacidade_pressionado")) e.putFloat("op_pressed", (float) o.optDouble("opacidade_pressionado", 1));
		JSONObject pos = o.optJSONObject("posicao");
		for (String n : PROFILE_POS_NAMES) {
			for (String o2 : new String[]{"_p", "_l"}) {
				for (String ax : new String[]{"x", "y"}) {
					String k = n + o2 + ax;
					if (pos != null && pos.has(k)) {
						e.putFloat(k, (float) pos.optDouble(k, 0));
					} else {
						e.remove(k);
					}
				}
			}
		}
		e.apply();
		invalidateCache();
	}

	/** Copia as imagens do analogico e do D-pad (tipo ativo) para a pasta do perfil. */
	public static synchronized void exportImages(File dstDir) {
		//noinspection ResultOfMethodCallIgnored
		dstDir.mkdirs();
		File[] old = dstDir.listFiles();
		if (old != null) {
			for (File f : old) {
				if (f.getName().endsWith(".png")) {
					//noinspection ResultOfMethodCallIgnored
					f.delete();
				}
			}
		}
		for (String id : PROFILE_IMG_IDS) {
			for (boolean pressed : new boolean[]{false, true}) {
				File src = imageFile(id, pressed);
				if (src.isFile()) {
					ImportStore.copy(src, new File(dstDir, imageName(id, pressed)));
				}
			}
		}
	}

	/** Traz as imagens gravadas no perfil para a pasta do tipo de jogo {@code type}. */
	public static synchronized void importImages(File srcDir, String type) {
		boolean any = false;
		for (String id : PROFILE_IMG_IDS) {
			for (boolean pressed : new boolean[]{false, true}) {
				File src = new File(srcDir, imageName(id, pressed));
				if (!src.isFile()) {
					continue;
				}
				File out = imageFile(type, id, pressed);
				//noinspection ResultOfMethodCallIgnored
				out.delete();
				ImportStore.copy(src, out);
				any = true;
			}
		}
		if (any) {
			invalidateCache();
			ImportStore.sync();
		}
	}
}
