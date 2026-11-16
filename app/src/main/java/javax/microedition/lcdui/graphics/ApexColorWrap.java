package javax.microedition.lcdui.graphics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Envolve o fragment shader com o controle de cores do app: gama, brilho, contraste, saturacao e LCD.
 * Se o shader tiver "#define CURVE_SOFTNESS <numero>" (4k resolution), esse valor passa a vir do
 * controle "Suavidade das curvas" (apex_extra.y).
 */
final class ApexColorWrap {
	private static final Pattern MAIN_FN = Pattern.compile("\\bvoid\\s+main\\s*\\(");
	private static final Pattern SOFT_DEF = Pattern.compile(
			"(?m)^[ \\t]*#define\\s+CURVE_SOFTNESS\\s+[0-9.]+[^\\n]*");

	private ApexColorWrap() {
	}

	static String wrap(String fragment) {
		if (fragment == null || fragment.contains("apex_main") || !MAIN_FN.matcher(fragment).find()) {
			return fragment;
		}
		String src = fragment;
		boolean soft = false;
		Matcher sm = SOFT_DEF.matcher(src);
		if (sm.find()) {
			src = sm.replaceFirst(Matcher.quoteReplacement(
					"uniform mediump vec4 apex_extra;\n#define CURVE_SOFTNESS apex_extra.y"));
			soft = true;
		}
		String body = MAIN_FN.matcher(src).replaceAll("void apex_main(");
		return body + "\n"
				+ (soft ? "" : "uniform mediump vec4 apex_extra;\n")
				+ "uniform mediump vec4 apex_color;\n"
				+ "uniform mediump vec4 apex_grid;\n"
				+ "void main() {\n"
				+ "  apex_main();\n"
				+ "  mediump vec3 c = gl_FragColor.rgb;\n"
				+ "  if (abs(apex_extra.x - 1.0) > 0.001) {\n"
				+ "    c = pow(max(c, vec3(0.0)), vec3(1.0 / max(apex_extra.x, 0.01)));\n"
				+ "  }\n"
				+ "  c += apex_color.x;\n"
				+ "  c = (c - 0.5) * apex_color.y + 0.5;\n"
				+ "  mediump float l = dot(c, vec3(0.299, 0.587, 0.114));\n"
				+ "  c = mix(vec3(l), c, apex_color.z);\n"
				+ "  if (apex_color.w > 0.001 && apex_grid.z > 0.5) {\n"
				+ "    mediump vec2 p = fract((gl_FragCoord.xy - apex_grid.xy) / apex_grid.zw);\n"
				+ "    mediump float gx = smoothstep(0.0, 0.22, p.x) * smoothstep(1.0, 0.78, p.x);\n"
				+ "    mediump float gy = smoothstep(0.0, 0.22, p.y) * smoothstep(1.0, 0.78, p.y);\n"
				+ "    mediump vec3 mask = vec3(0.62);\n"
				+ "    if (p.x < 0.3333) mask.r = 1.0;\n"
				+ "    else if (p.x < 0.6666) mask.g = 1.0;\n"
				+ "    else mask.b = 1.0;\n"
				+ "    mediump vec3 lcd = c * mask * (0.45 + 0.55 * gx * gy) * 1.35;\n"
				+ "    c = mix(c, lcd, apex_color.w);\n"
				+ "  }\n"
				+ "  gl_FragColor.rgb = clamp(c, 0.0, 1.0);\n"
				+ "}\n";
	}
}
