/*
 *  Copyright 2020 Yury Kharchenko
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package javax.microedition.lcdui.graphics;

import android.opengl.GLU;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.nio.FloatBuffer;

import javax.microedition.lcdui.ViewHandler;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.apex.ApexPrefs;
import ru.playsoftware.j2meloader.config.ShaderInfo;
import ru.playsoftware.j2meloader.util.FileUtils;

import static android.opengl.GLES20.*;

public class ShaderProgram {
	private static final String TAG = ShaderProgram.class.getName();
	private static final String VERTEX = "shaders/simple.vsh";
	private static final String FRAGMENT = "shaders/simple.fsh";
	private static final String APEX_FRAGMENT = "shaders/apex.fsh";

	public int aTexCoord;
	public int uTextureUnit;
	public int aPosition;
	public int uTexelDelta;
	public int uSetting;
	public int uPixelDelta;
	public int uColor;
	// nomes no estilo RetroArch (GLSL ES 2.0)
	public int uTextureSize;
	public int uInputSize;
	public int uOutputSize;
	public int uFrameCount;
	public int uMvp;
	public int uResolution;
	public int uTime;
	private final long startTime = System.nanoTime();
	/** Ultimo erro de compilacao de um shader importado (para mostrar ao usuario). */
	static volatile String lastError;
	private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

	public ShaderProgram(ShaderInfo shader) {
		String sel = ApexPrefs.shaderSel();
		if (sel.startsWith(ApexPrefs.SEL_USER)) {
			File f = ApexPrefs.userShaderFile(sel.substring(ApexPrefs.SEL_USER.length()));
			if (f.exists()) {
				String src = clean(FileUtils.getText(f.getPath()));
				String[] codes = buildCodes(f, src);
				String vertexCode = codes[0];
				String fragmentCode = codes[1];
				lastError = null;
				int userProgram = createProgram(vertexCode, fragmentCode);
				if (userProgram != -1) {
					applyPragmaDefaults(userProgram, src);
					glReleaseShaderCompiler();
					return;
				}
				final String err = lastError;
				ApexPrefs.markShaderBad(sel.substring(ApexPrefs.SEL_USER.length()));
				ApexPrefs.setShaderSel(ApexPrefs.SEL_AUTO);
				ViewHandler.postEvent(() -> Toast.makeText(ContextHolder.getActivity(),
						"Shader incompativel (removido da lista) - usando o padrao!"
								+ (err == null ? "" : "\n" + (err.length() > 160 ? err.substring(0, 160) : err)),
						Toast.LENGTH_LONG).show());
			}
		} else if (ApexPrefs.SEL_BUILTIN.equals(sel) || shader == null) {
			String vertexCode = ContextHolder.getAssetAsString(VERTEX);
			String fragmentCode = ContextHolder.getAssetAsString(APEX_FRAGMENT);
			if (createProgram(vertexCode, fragmentCode) != -1) {
				glReleaseShaderCompiler();
				return;
			}
		}
		if (shader != null && !sel.startsWith(ApexPrefs.SEL_USER) && !ApexPrefs.SEL_BUILTIN.equals(sel)) {
			String vertex = shader.vertex;
			String fragment = shader.fragment;
			if (vertex != null && fragment != null) {
				String vertexCode = FileUtils.getText(shader.dir + vertex);
				String fragmentCode = FileUtils.getText(shader.dir + fragment);
				if (createProgram(vertexCode, fragmentCode) != -1) {
					glReleaseShaderCompiler();
					return;
				}
				ViewHandler.postEvent(() -> Toast.makeText(ContextHolder.getActivity(),
						"Error loading shader - default shader is used!",
						Toast.LENGTH_LONG).show());
			}
		}
		String vertexCode = ContextHolder.getAssetAsString(VERTEX);
		String fragmentCode = ContextHolder.getAssetAsString(FRAGMENT);
		int program = createProgram(vertexCode, fragmentCode);
		glReleaseShaderCompiler();
		if (program == -1) {
			throw new RuntimeException("Init shader program error: see log for detail");
		}
	}

	private ShaderProgram() {
		// so para testUserShader
	}

	/** Monta {vertex, fragment} de um shader importado (arquivo unico, par vertex/fragment ou so fragment). */
	private static String[] buildCodes(File f, String src) {
		String vertexCode;
		String fragmentCode;
		File mate = findMate(f);
		if (isCombined(src)) {
			// arquivo unico (RetroArch): o mesmo codigo compilado como vertex e como fragment
			vertexCode = prepare(src, "VERTEX");
			fragmentCode = prepare(src, "FRAGMENT");
		} else if (mate != null) {
			// par .vsh/.fsh (.vert/.frag, .vs/.fs) com o mesmo nome
			String a = clean(FileUtils.getText(mate.getPath()));
			boolean fIsVertex = isVertexName(f.getName());
			vertexCode = prepare(fIsVertex ? src : a, "VERTEX");
			fragmentCode = prepare(fIsVertex ? a : src, "FRAGMENT");
		} else {
			vertexCode = ContextHolder.getAssetAsString(VERTEX);
			fragmentCode = prepare(src, "FRAGMENT");
		}
		return new String[]{vertexCode, fragmentCode};
	}

	/** Acompanha o teste de todos os shaders. Chamado na thread do OpenGL. */
	public interface TestListener {
		void onProgress(int done, int total, int bad);

		void onDone(int ok, int bad, boolean cancelled);

		boolean isCancelled();
	}

	/**
	 * Compila e liga um shader importado so para ver se funciona (sem usa-lo).
	 * Precisa rodar na thread do OpenGL. Devolve false se der erro ou se faltar posicao/textura.
	 */
	public static boolean testUserShader(String name) {
		try {
			File f = ApexPrefs.userShaderFile(name);
			if (!f.isFile()) {
				return false;
			}
			String[] codes = buildCodes(f, clean(FileUtils.getText(f.getPath())));
			ShaderProgram p = new ShaderProgram();
			lastError = null;
			int prog = p.createProgram(codes[0], codes[1]);
			if (prog == -1) {
				return false;
			}
			boolean ok = p.aPosition != -1 && p.aTexCoord != -1;
			int[] count = new int[1];
			int[] ids = new int[4];
			glGetAttachedShaders(prog, ids.length, count, 0, ids, 0);
			glUseProgram(0);
			glDeleteProgram(prog);
			for (int i = 0; i < count[0]; i++) {
				glDeleteShader(ids[i]);
			}
			glGetError();
			return ok;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Shader de arquivo unico: usa #if defined(VERTEX) / FRAGMENT. */
	public static boolean isCombined(String src) {
		return src.contains("VERTEX") && src.contains("FRAGMENT")
				&& java.util.regex.Pattern.compile("#\\s*(if|ifdef|ifndef|elif)[^\\n]*\\b(VERTEX|FRAGMENT)\\b")
				.matcher(src).find();
	}

	private static final java.util.regex.Pattern PRAGMA_PARAM = java.util.regex.Pattern.compile(
			"(?m)^[ \\t]*#pragma\\s+parameter\\s+(\\w+)\\s+\"[^\"]*\"\\s+(-?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)");

	/**
	 * Shaders do libretro declaram os parametros em "#pragma parameter NOME "texto" padrao min max passo".
	 * Quando o shader os recebe como uniform, ninguem os preenche aqui, entao ficariam em 0: aplica o padrao.
	 * O programa precisa estar em uso (createProgram deixa em uso).
	 */
	private static void applyPragmaDefaults(int program, String src) {
		java.util.regex.Matcher m = PRAGMA_PARAM.matcher(src);
		while (m.find()) {
			int loc = glGetUniformLocation(program, m.group(1));
			if (loc == -1) {
				continue;
			}
			try {
				glUniform1f(loc, Float.parseFloat(m.group(2)));
			} catch (NumberFormatException ignored) {
			}
		}
	}

	private static boolean isVertexName(String n) {
		n = n.toLowerCase(java.util.Locale.ROOT);
		return n.endsWith(".vsh") || n.endsWith(".vert") || n.endsWith(".vs");
	}

	/** Procura o arquivo par (vertex <-> fragment) com o mesmo nome base, na mesma pasta. */
	private static File findMate(File f) {
		String n = f.getName();
		int dot = n.lastIndexOf('.');
		if (dot <= 0) {
			return null;
		}
		String base = n.substring(0, dot);
		String[] exts = isVertexName(n) ? new String[]{".fsh", ".frag", ".fs", ".glsl"}
				: new String[]{".vsh", ".vert", ".vs"};
		if (!isVertexName(n) && !n.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(fsh|frag|fs|glsl|txt|shader)$")) {
			return null;
		}
		for (String e : exts) {
			File m = new File(f.getParentFile(), base + e);
			if (m.isFile() && !m.equals(f)) {
				return m;
			}
		}
		return null;
	}

	/** Tira BOM, quebras de linha do Windows e caracteres nulos. */
	private static String clean(String s) {
		if (s == null) {
			return "";
		}
		if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
			s = s.substring(1);
		}
		return s.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "");
	}

	/**
	 * Deixa o shader compilavel no GLES 2.0: define o estagio, remove #version de desktop,
	 * converte GLSL 130+/300 es para 100, completa a precisao e aceita o formato ShaderToy (mainImage).
	 */
	private static String prepare(String src, String stage) {
		String body = src;
		boolean modern = false;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^[ \\t]*#version[^\n]*\n?").matcher(src);
		if (m.find()) {
			String v = m.group().trim();
			// o #version sai: o contexto e GLES 2.0 (100). Versoes novas sao convertidas abaixo
			modern = !v.matches("#version\\s+(100|110|120)\\s*(es)?.*");
			body = src.substring(0, m.start()) + "\n" + src.substring(m.end());
		}
		boolean vertex = "VERTEX".equals(stage);
		if (modern) {
			body = body.replaceAll("\\btexture(2D|Lod|Proj)?\\s*\\(", "texture2D(");
			body = body.replaceAll("\\btexelFetch\\s*\\(", "texture2D(");
			if (vertex) {
				body = body.replaceAll("(?m)^([ \\t]*)(flat\\s+|smooth\\s+)?in\\s+", "$1attribute ");
				body = body.replaceAll("(?m)^([ \\t]*)(flat\\s+|smooth\\s+)?out\\s+", "$1varying ");
			} else {
				java.util.regex.Matcher o = java.util.regex.Pattern
						.compile("(?m)^[ \\t]*(layout\\s*\\([^)]*\\)\\s*)?out\\s+(?:(?:lowp|mediump|highp)\\s+)?vec4\\s+(\\w+)\\s*;").matcher(body);
				if (o.find()) {
					String name = o.group(2);
					body = body.substring(0, o.start()) + body.substring(o.end());
					body = body.replaceAll("\\b" + name + "\\b", "gl_FragColor");
				}
				body = body.replaceAll("(?m)^([ \\t]*)(flat\\s+|smooth\\s+)?in\\s+", "$1varying ");
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("#define ").append(stage).append(" 1\n");
		if (!vertex && !body.contains("precision ")) {
			sb.append("#ifdef GL_ES\nprecision mediump float;\n#endif\n");
		}
		boolean toy = !vertex && body.contains("mainImage") && !java.util.regex.Pattern
				.compile("void\\s+main\\s*\\(").matcher(body).find();
		sb.append(body);
		if (toy) {
			sb.append("\nvarying vec2 v_texcoord0;\nvoid main() {\n")
					.append("  vec4 c = vec4(0.0);\n  mainImage(c, v_texcoord0 * iResolution.xy);\n")
					.append("  gl_FragColor = vec4(c.rgb, 1.0);\n}\n");
			// uniforms do ShaderToy que o arquivo nao declarou
			String head = "";
			if (!body.contains("uniform vec3 iResolution")) head += "uniform vec3 iResolution;\n";
			if (!body.contains("uniform float iTime") && !body.contains("uniform float iGlobalTime")) head += "uniform float iTime;\n";
			if (!body.contains("uniform sampler2D iChannel0")) head += "uniform sampler2D iChannel0;\n";
			String all = sb.toString();
			int at = all.indexOf('\n', all.indexOf("#define")) + 1;
			return all.substring(0, at) + head + all.substring(at);
		}
		return sb.toString();
	}

	public int createProgram(String vertex, String fragment) {
		int vertexId = loadShader(GL_VERTEX_SHADER, vertex);
		int fragmentId = loadShader(GL_FRAGMENT_SHADER, fragment);
		if (vertexId == -1 || fragmentId == -1) {
			return -1;
		}

		int program = glCreateProgram();     // create empty OpenGL ShaderProgram
		glAttachShader(program, vertexId);   // add the vertex shader to program
		glAttachShader(program, fragmentId); // add the fragment shader to program

		glLinkProgram(program);              // create OpenGL program executables
		int[] status = new int[1];
		glGetProgramiv(program, GL_LINK_STATUS, status, 0);
		if (status[0] == 0) {
			String s = glGetProgramInfoLog(program);
			Log.e(TAG, "createProgram: " + s);
			lastError = s;
			glDeleteShader(vertexId);
			glDeleteShader(fragmentId);
			glDeleteProgram(program);
			return -1;
		}
		int error = glGetError();
		if (error != GL_NO_ERROR) {
			String errorString = GLU.gluErrorString(error);
			Log.e(TAG, "init program: glError " + errorString);
		}
		aPosition = glGetAttribLocation(program, "a_position");
		if (aPosition == -1) aPosition = glGetAttribLocation(program, "VertexCoord");
		aTexCoord = glGetAttribLocation(program, "a_texcoord0");
		if (aTexCoord == -1) aTexCoord = glGetAttribLocation(program, "TexCoord");
		if (aPosition == -1) aPosition = glGetAttribLocation(program, "aPosition");
		if (aPosition == -1) aPosition = glGetAttribLocation(program, "a_pos");
		if (aPosition == -1) aPosition = glGetAttribLocation(program, "position");
		if (aTexCoord == -1) aTexCoord = glGetAttribLocation(program, "aTexCoord");
		if (aTexCoord == -1) aTexCoord = glGetAttribLocation(program, "a_texCoord");
		if (aTexCoord == -1) aTexCoord = glGetAttribLocation(program, "texcoord");
		uTextureUnit = glGetUniformLocation(program, "sampler0");
		if (uTextureUnit == -1) uTextureUnit = glGetUniformLocation(program, "Texture");
		if (uTextureUnit == -1) uTextureUnit = glGetUniformLocation(program, "iChannel0");
		if (uTextureUnit == -1) uTextureUnit = glGetUniformLocation(program, "u_texture");
		if (uTextureUnit == -1) uTextureUnit = glGetUniformLocation(program, "tex");
		if (uTextureUnit == -1) uTextureUnit = glGetUniformLocation(program, "uTexture");
		uResolution = glGetUniformLocation(program, "iResolution");
		uTime = glGetUniformLocation(program, "iTime");
		if (uTime == -1) uTime = glGetUniformLocation(program, "iGlobalTime");
		uTextureSize = glGetUniformLocation(program, "TextureSize");
		uInputSize = glGetUniformLocation(program, "InputSize");
		uOutputSize = glGetUniformLocation(program, "OutputSize");
		uFrameCount = glGetUniformLocation(program, "FrameCount");
		uMvp = glGetUniformLocation(program, "MVPMatrix");
		uTexelDelta = glGetUniformLocation(program, "u_texelDelta");
		uPixelDelta = glGetUniformLocation(program, "u_pixelDelta");
		uSetting = glGetUniformLocation(program, "u_setting");
		uColor = glGetUniformLocation(program, "u_color");
		glUseProgram(program);
		if (uMvp != -1) {
			glUniformMatrix4fv(uMvp, 1, false, IDENTITY, 0);
		}
		int error1 = glGetError();
		if (error1 != GL_NO_ERROR) {
			String s1 = GLU.gluErrorString(error1);
			Log.e(TAG, "init program: glError " + s1);
			glDeleteShader(vertexId);
			glDeleteShader(fragmentId);
			glDeleteProgram(program);
			return -1;
		}
		return program;
	}

	private int loadShader(int type, String shaderCode) {

		// create a vertex shader type (GLES20.GL_VERTEX_SHADER)
		// or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
		int shader = glCreateShader(type);

		// add the source code to the shader and compile it
		glShaderSource(shader, shaderCode);
		glCompileShader(shader);
		int[] status = new int[1];
		glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0);
		if (status[0] == 0) {
			String s = glGetShaderInfoLog(shader);
			Log.e(TAG, "loadShader: " + s);
			lastError = s;
			glDeleteShader(shader);
			return -1;
		}
		return shader;
	}

	public void loadVbo(FloatBuffer vbo, float width, float height) {
		vbo.rewind();
		glVertexAttribPointer(aPosition, 2, GL_FLOAT, false, 4 * 4, vbo);
		glEnableVertexAttribArray(aPosition);

		// координаты текстур
		vbo.position(2);
		glVertexAttribPointer(aTexCoord, 2, GL_FLOAT, false, 4 * 4, vbo);
		glEnableVertexAttribArray(aTexCoord);
		glUniform2f(uTexelDelta, 1.0f / width, 1.0f / height);
		glUniform2f(uTextureSize, width, height);
		glUniform2f(uInputSize, width, height);
		if (uResolution != -1) glUniform3f(uResolution, width, height, 1f);
		if (uTime != -1) glUniform1f(uTime, (System.nanoTime() - startTime) / 1e9f);
	}
}
