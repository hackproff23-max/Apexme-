package ru.playsoftware.j2meloader.apex;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.microedition.lcdui.graphics.ShaderProgram;

/**
 * Baixa o repositorio libretro/glsl-shaders (GitHub) e importa so os shaders que o emulador consegue usar:
 * arquivo unico (#if defined(VERTEX) / FRAGMENT) que usa apenas a textura do jogo. Os que dependem de
 * outros passes, imagens de LUT ou de frames anteriores (presets .glslp) sao ignorados.
 * Os arquivos ficam na pasta shaders/ do app com o prefixo "libretro__".
 */
public final class LibretroShaders {
	public static final String PREFIX = "libretro__";
	private static final String URL_ZIP = "https://github.com/libretro/glsl-shaders/archive/refs/heads/master.zip";
	private static final int MAX_FILE = 512 * 1024;
	private static final Pattern SAMPLER = Pattern.compile("uniform\\s+(?:(?:lowp|mediump|highp|COMPAT_PRECISION)\\s+)?sampler2D\\s+(\\w+)");
	private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(");

	/** Acompanha o andamento e permite cancelar. Chamado fora da thread principal. */
	public interface Listener {
		void onProgress(int scanned, int imported);

		boolean isCancelled();
	}

	public static final class Result {
		public int imported;
		public int skipped;
		public boolean cancelled;
		/** Mensagem de erro (rede, etc.) ou null. */
		public String error;
	}

	private LibretroShaders() {
	}

	/** Recursos que o GLES 2.0 (J2ME) nao tem ou que exigem varios passes. */
	private static final Pattern UNSUPPORTED = Pattern.compile(
			"\\b(texelFetch|textureSize|textureGrad|textureOffset|uint|uvec[234]|ivec[234]|imageLoad|imageStore|"
					+ "gl_FragData|gl_VertexID|gl_InstanceID|PassPrev\\d*Texture|OrigTexture|PrevTexture|"
					+ "Original|OriginalHistory\\d*|PassOutput\\d*|FeedbackTexture)\\b|layout\\s*\\(|#\\s*extension\\s+\\w+\\s*:\\s*require");
	private static final Pattern HEADER_BAD = Pattern.compile(
			"(?m)^\\s*(#version\\s+(?:[3-9]\\d\\d|1[3-9]\\d)(?!\\s*es)(?:\\s+core)?\\s*$|#pragma\\s+(format|alias)\\b)");

	/**
	 * Le o cabecalho (primeiras linhas: #version, #pragma) e o final do arquivo para descartar cedo o que
	 * o J2ME nao roda: GLSL desktop 130+, formatos de framebuffer (multi-pass), main sem fechar etc.
	 */
	static boolean quickCheck(String src) {
		String[] lines = src.split("\n");
		StringBuilder head = new StringBuilder();
		for (int i = 0; i < lines.length && i < 40; i++) {
			head.append(lines[i]).append('\n');
		}
		if (HEADER_BAD.matcher(head).find()) {
			return false;
		}
		// ultima linha com conteudo: arquivo truncado (chaves desbalanceadas) nao compila
		int open = 0;
		for (int i = 0; i < src.length(); i++) {
			char ch = src.charAt(i);
			if (ch == '{') open++;
			else if (ch == '}') open--;
		}
		return open == 0 && !UNSUPPORTED.matcher(src).find();
	}

	/** True se o shader e de arquivo unico e usa so a textura do jogo (sampler "Texture"). */
	static boolean isCompatible(String src) {
		if (!quickCheck(src) || src.length() > MAX_FILE || !MAIN.matcher(src).find() || !ShaderProgram.isCombined(src)) {
			return false;
		}
		Matcher m = SAMPLER.matcher(src);
		while (m.find()) {
			if (!"Texture".equals(m.group(1))) {
				return false; // passes anteriores, LUTs, frames passados...
			}
		}
		return true;
	}

	/** crt/shaders/crt-geom.glsl -> libretro__crt__crt-geom.glsl */
	static String flatName(String path) {
		StringBuilder sb = new StringBuilder(PREFIX);
		String[] parts = path.split("/");
		boolean first = true;
		for (int i = 0; i < parts.length; i++) {
			String p = parts[i];
			if (p.isEmpty() || (p.equals("shaders") && i < parts.length - 1)) {
				continue;
			}
			if (!first) {
				sb.append("__");
			}
			sb.append(p);
			first = false;
		}
		String n = sb.toString().replaceAll("[^A-Za-z0-9._-]", "_");
		return n.length() > 120 ? n.substring(0, 100) + n.substring(n.length() - 20) : n;
	}

	public static Result download(Listener l) {
		Result r = new Result();
		HttpURLConnection c = null;
		try {
			c = (HttpURLConnection) new URL(URL_ZIP).openConnection();
			c.setConnectTimeout(20000);
			c.setReadTimeout(30000);
			c.setRequestProperty("User-Agent", "Apexme");
			if (c.getResponseCode() != 200) {
				r.error = "Servidor respondeu " + c.getResponseCode();
				return r;
			}
			HashSet<String> used = new HashSet<>();
			int scanned = 0;
			try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(c.getInputStream(), 64 * 1024))) {
				ZipEntry e;
				while ((e = zin.getNextEntry()) != null) {
					if (l.isCancelled()) {
						r.cancelled = true;
						break;
					}
					String path = e.getName();
					int slash = path.indexOf('/');
					path = slash >= 0 ? path.substring(slash + 1) : path; // tira "glsl-shaders-master/"
					if (e.isDirectory() || !path.endsWith(".glsl") || path.contains("template")) {
						continue;
					}
					scanned++;
					byte[] data = readLimited(zin);
					String src = data == null ? null : new String(data, "UTF-8");
					if (src != null && isCompatible(src)) {
						String name = flatName(path);
						while (!used.add(name)) {
							name = name.replace(".glsl", "_2.glsl");
						}
						File out = ApexPrefs.userShaderFile(name);
						try (FileOutputStream os = new FileOutputStream(out)) {
							os.write(data);
						}
						r.imported++;
					} else {
						r.skipped++;
					}
					if (scanned % 20 == 0) {
						l.onProgress(scanned, r.imported);
					}
				}
			}
			l.onProgress(scanned, r.imported);
		} catch (Exception ex) {
			r.error = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
		} finally {
			if (c != null) {
				c.disconnect();
			}
			ImportStore.sync();
		}
		return r;
	}

	/** Le a entrada atual do zip; devolve null se passar do limite. */
	private static byte[] readLimited(InputStream in) throws java.io.IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(16 * 1024);
		byte[] buf = new byte[8192];
		int n;
		boolean tooBig = false;
		while ((n = in.read(buf)) > 0) {
			if (!tooBig) {
				bos.write(buf, 0, n);
				if (bos.size() > MAX_FILE) {
					tooBig = true;
				}
			}
		}
		return tooBig ? null : bos.toByteArray();
	}

	/** Apaga os shaders baixados do libretro. Devolve quantos foram removidos. */
	public static int removeAll() {
		int n = 0;
		String sel = ApexPrefs.shaderSel();
		for (String name : ApexPrefs.listUserShaders()) {
			if (name.startsWith(PREFIX) && ApexPrefs.userShaderFile(name).delete()) {
				n++;
			}
		}
		if (sel.startsWith(ApexPrefs.SEL_USER + PREFIX)) {
			ApexPrefs.setShaderSel(ApexPrefs.SEL_AUTO);
		}
		ImportStore.sync();
		return n;
	}
}
