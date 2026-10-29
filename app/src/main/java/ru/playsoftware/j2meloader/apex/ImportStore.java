package ru.playsoftware.j2meloader.apex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.theme.ApexTheme;

/**
 * Pasta unica de importacoes do Apexme.
 *
 * <pre>
 * Apexme/
 *   importacao/
 *     botoes/    imagens dos botoes (ID_normal.png)
 *     fundos/    fundo do app e fundo das janelas
 *     fontes/    fonte .ttf/.otf
 *     LEIA-ME.txt
 *   shaders/       shaders importados e baixados (libretro__*)
 *   configuracoes/
 *     importacoes.json   lista organizada de tudo que foi importado
 *     tema.json          cores e arquivos de tema em uso
 * </pre>
 *
 * Se a pasta do emulador nao estiver disponivel, usa a pasta interna do app como reserva
 * e leva os arquivos para importacao/ assim que ela ficar pronta ({@link #migrate}).
 */
public final class ImportStore {
	public static final String BOTOES = "botoes";
	public static final String FUNDOS = "fundos";
	public static final String FONTES = "fontes";
	public static final String SHADERS = "shaders";

	private static final String JSON_FILE = "importacoes.json";
	private static final String README_FILE = "LEIA-ME.txt";

	private ImportStore() {
	}

	/** Subpasta de importacao/ no armazenamento do emulador, ou null se nao estiver gravavel. */
	private static File externalDir(String sub) {
		try {
			if (Config.getEmulatorDir() == null) {
				return null;
			}
			// shaders ficam na pasta shaders/ do app (ao lado de importacao/), nao dentro dela
			File d = SHADERS.equals(sub) ? new File(Config.getShadersDir()) : new File(Config.getImportDir(), sub);
			if (!d.isDirectory() && !d.mkdirs()) {
				return null;
			}
			return d.canWrite() ? d : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static File internalDir(String sub) {
		File d = new File(ContextHolder.getAppContext().getFilesDir(), "apex/" + sub);
		//noinspection ResultOfMethodCallIgnored
		d.mkdirs();
		return d;
	}

	/** Pasta de uma categoria: importacao/SUB, ou a pasta interna se a externa nao estiver pronta. */
	public static File dir(String sub) {
		File d = externalDir(sub);
		return d != null ? d : internalDir(sub);
	}

	/** Subpasta das imagens de botoes de um tipo de jogo: botoes/padrao, botoes/tiro, botoes/futebol. */
	public static String botoesSub(String type) {
		return BOTOES + "/" + type;
	}

	/** Move os arquivos de src para dst (sem entrar em subpastas). */
	private static void moveFiles(File src, File dst) {
		if (src == null || dst == null || src.equals(dst)) {
			return;
		}
		File[] old = src.listFiles();
		if (old == null) {
			return;
		}
		for (File f : old) {
			if (!f.isFile()) {
				continue;
			}
			File out = new File(dst, f.getName());
			if (out.exists() || copy(f, out)) {
				//noinspection ResultOfMethodCallIgnored
				f.delete();
			}
		}
	}

	/** Leva arquivos da pasta interna (versoes antigas ou sem permissao) para importacao/ e atualiza o JSON. */
	public static synchronized void migrate() {
		File internal = new File(ContextHolder.getAppContext().getFilesDir(), "apex");
		File padrao = externalDir(botoesSub(ApexPrefs.GAME_PADRAO));
		if (padrao != null) {
			// pastas internas antigas e imagens soltas direto em importacao/botoes/ viram "padrao"
			moveFiles(new File(internal, "buttons"), padrao);
			moveFiles(new File(internal, BOTOES), padrao);
			moveFiles(externalDir(BOTOES), padrao);
		}
		for (String type : ApexPrefs.GAME_TYPES) {
			// cria as pastas de cada tipo e leva o que ficou na reserva interna
			moveFiles(new File(internal, botoesSub(type)), externalDir(botoesSub(type)));
		}
		File shadersDir = externalDir(SHADERS);
		moveFiles(new File(internal, SHADERS), shadersDir);
		// versoes anteriores guardavam os shaders em importacao/shaders: leva para shaders/
		File oldShaders = new File(Config.getImportDir(), SHADERS);
		moveFiles(oldShaders, shadersDir);
		if (shadersDir != null) {
			//noinspection ResultOfMethodCallIgnored
			oldShaders.delete(); // so apaga se ficou vazia
		}
		sync();
	}

	/** Recria configuracoes/importacoes.json olhando o que existe de verdade nas pastas. */
	public static synchronized void sync() {
		try {
			File imp = externalDir(BOTOES);
			if (imp == null) {
				return;
			}
			File root = imp.getParentFile();
			File cfgDir = new File(Config.getSettingsDir());
			if (!cfgDir.isDirectory() && !cfgDir.mkdirs()) {
				return;
			}
			Context c = ContextHolder.getAppContext();
			JSONObject o = new JSONObject();
			o.put("versao", 1);
			o.put("atualizado_em", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()));
			o.put("pasta", root.getPath());

			// botoes: um grupo por tipo de jogo, todos os ids, com o arquivo quando existir
			JSONObject botoes = new JSONObject();
			o.put("tipo_em_uso", ApexPrefs.gameType());
			o.put("dica_botoes", "Para trocar na mao, coloque ID_normal.png em importacao/botoes/TIPO/ "
					+ "(padrao, tiro ou futebol) e reabra o app.");
			for (String type : ApexPrefs.GAME_TYPES) {
				File td = externalDir(botoesSub(type));
				JSONObject grupo = new JSONObject();
				for (String[] b : ApexPrefs.allButtons()) {
					JSONObject e = new JSONObject();
					e.put("nome", b[1]);
					File f = td != null ? new File(td, b[0].replaceAll("[^A-Za-z0-9_]", "_") + "_normal.png") : null;
					e.put("arquivo", f != null && f.isFile() ? f.getName() : JSONObject.NULL);
					grupo.put(b[0], e);
				}
				botoes.put(type, grupo);
			}
			o.put("botoes", botoes);

			// fundos e fonte em uso
			JSONObject fundos = new JSONObject();
			fundos.put("app", nameOrNull(ApexTheme.inUseName(c, ApexTheme.TARGET_APP_BG)));
			fundos.put("janela", nameOrNull(ApexTheme.inUseName(c, ApexTheme.TARGET_WINDOW_BG)));
			fundos.put("arquivos", list(externalDir(FUNDOS)));
			o.put("fundos", fundos);

			JSONObject fontes = new JSONObject();
			fontes.put("em_uso", nameOrNull(ApexTheme.inUseName(c, ApexTheme.TARGET_FONT)));
			fontes.put("arquivos", list(externalDir(FONTES)));
			o.put("fontes", fontes);

			// shaders
			JSONArray shaders = new JSONArray();
			String sel = ApexPrefs.shaderSel();
			File sd = externalDir(SHADERS);
			String[] names = sd != null ? ApexPrefs.shaderFileNames(sd) : null;
			if (names != null) {
				for (String n : names) {
					JSONObject e = new JSONObject();
					e.put("arquivo", n);
					e.put("em_uso", sel != null && sel.equals(ApexPrefs.SEL_USER + n));
					shaders.put(e);
				}
			}
			o.put("shaders", shaders);

			try (FileOutputStream os = new FileOutputStream(new File(cfgDir, JSON_FILE))) {
				os.write(o.toString(2).getBytes("UTF-8"));
			}
			writeReadme(root);
		} catch (Throwable ignored) {
		}
	}

	private static Object nameOrNull(String s) {
		return s != null ? s : JSONObject.NULL;
	}

	private static JSONArray list(File d) {
		JSONArray a = new JSONArray();
		String[] names = d != null ? d.list() : null;
		if (names != null) {
			Arrays.sort(names);
			for (String n : names) {
				a.put(n);
			}
		}
		return a;
	}

	private static void writeReadme(File root) {
		File f = new File(root, README_FILE);
		String t = "Pasta de importacoes do Apexme\n\n"
				+ "botoes/padrao/   imagens dos botoes do tipo Padrao (nome: ID_normal.png)\n"
				+ "botoes/tiro/     imagens dos botoes do tipo Tiro\n"
				+ "botoes/futebol/  imagens dos botoes do tipo Futebol\n"
				+ "                 (o tipo ativo e escolhido no Teclado virtual)\n"
				+ "fundos/   fundo do app e fundo das janelas\n"
				+ "fontes/   fonte .ttf ou .otf\n"
				+ "\nOs shaders (importados e baixados do libretro) ficam em shaders/, ao lado\n"
				+ "de importacao/ (.fsh, .frag, .glsl).\n\n"
				+ "A lista completa e organizada fica em configuracoes/importacoes.json\n"
				+ "(os IDs dos botoes estao la). O tema em uso fica em configuracoes/tema.json.\n";
		try (FileOutputStream os = new FileOutputStream(f)) {
			os.write(t.getBytes("UTF-8"));
		} catch (Exception ignored) {
		}
	}

	public static boolean copy(File src, File dst) {
		try (InputStream in = new FileInputStream(src); OutputStream os = new FileOutputStream(dst)) {
			byte[] buf = new byte[16 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) {
				os.write(buf, 0, n);
			}
			return true;
		} catch (Exception e) {
			//noinspection ResultOfMethodCallIgnored
			dst.delete();
			return false;
		}
	}
}
