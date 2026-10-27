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
	/** Ultimo erro de compilacao de um shader importado (para mostrar ao usuario). */
	static volatile String lastError;
	private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

	public ShaderProgram(ShaderInfo shader) {
		String sel = ApexPrefs.shaderSel();
		if (sel.startsWith(ApexPrefs.SEL_USER)) {
			File f = ApexPrefs.userShaderFile(sel.substring(ApexPrefs.SEL_USER.length()));
			if (f.exists()) {
				String src = FileUtils.getText(f.getPath());
				String vertexCode;
				String fragmentCode;
				if (isCombined(src)) {
					// arquivo unico (RetroArch): o mesmo codigo compilado como vertex e como fragment
					vertexCode = prepare(src, "VERTEX");
					fragmentCode = prepare(src, "FRAGMENT");
				} else {
					vertexCode = ContextHolder.getAssetAsString(VERTEX);
					fragmentCode = src;
				}
				lastError = null;
				if (createProgram(vertexCode, fragmentCode) != -1) {
					glReleaseShaderCompiler();
					return;
				}
				final String err = lastError;
				ViewHandler.postEvent(() -> Toast.makeText(ContextHolder.getActivity(),
						"Erro no shader importado - usando o padrao!"
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

	/** Shader de arquivo unico: usa #if defined(VERTEX) / FRAGMENT. */
	private static boolean isCombined(String src) {
		return src.contains("VERTEX") && src.contains("FRAGMENT")
				&& java.util.regex.Pattern.compile("#\\s*(if|ifdef|ifndef|elif)[^\\n]*\\b(VERTEX|FRAGMENT)\\b")
				.matcher(src).find();
	}

	/** Adiciona o define do estagio (e precisao) depois da linha #version, se houver. GLSL ES 2.0 = #version 100. */
	private static String prepare(String src, String stage) {
		String version = "";
		String body = src;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*#version[^\\n]*\\n").matcher(src);
		if (m.find()) {
			String v = m.group().trim();
			// versoes de desktop (110/120/...) nao compilam no Android: remove; 100 e 300 es ficam
			if (v.matches("#version\\s+(100|300\\s+es)")) {
				version = m.group();
			}
			body = src.substring(0, m.start()) + src.substring(m.end());
		}
		StringBuilder sb = new StringBuilder(version);
		sb.append("#define ").append(stage).append(" 1\n");
		sb.append("#ifdef GL_ES\nprecision mediump float;\n#endif\n");
		return sb.append(body).toString();
	}

	public int createProgram(String vertex, String fragment) {
		int vertexId = loadShader(GL_VERTEX_SHADER, vertex);
		int fragmentId = loadShader(GL_FRAGMENT_SHADER, fragment);

		int program = glCreateProgram();     // create empty OpenGL ShaderProgram
		glAttachShader(program, vertexId);   // add the vertex shader to program
		glAttachShader(program, fragmentId); // add the fragment shader to program

		glLinkProgram(program);              // create OpenGL program executables
		int[] status = new int[1];
		glGetProgramiv(program, GL_LINK_STATUS, status, 0);
		if (status[0] == 0) {
			String s = glGetProgramInfoLog(program);
			Log.e(TAG, "createProgram: " + s);
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
		uTextureUnit = glGetUniformLocation(program, "sampler0");
		if (uTextureUnit == -1) uTextureUnit = glGetUniformLocation(program, "Texture");
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
	}
}
