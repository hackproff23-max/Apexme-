precision mediump float;
uniform sampler2D sampler0;
uniform vec2 u_texelDelta;
uniform vec4 u_color; // x=brilho, y=contraste, z=saturacao, w=opacidade LCD
varying vec2 v_texcoord0;
void main() {
    vec3 c = texture2D(sampler0, v_texcoord0).rgb;
    c += u_color.x;
    c = (c - 0.5) * u_color.y + 0.5;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, u_color.z);
    if (u_color.w > 0.001) {
        vec2 p = fract(v_texcoord0 / u_texelDelta);
        float gx = smoothstep(0.0, 0.22, p.x) * smoothstep(1.0, 0.78, p.x);
        float gy = smoothstep(0.0, 0.22, p.y) * smoothstep(1.0, 0.78, p.y);
        float grid = gx * gy;
        vec3 mask = vec3(0.62);
        if (p.x < 0.3333) mask.r = 1.0;
        else if (p.x < 0.6666) mask.g = 1.0;
        else mask.b = 1.0;
        vec3 lcd = c * mask * (0.45 + 0.55 * grid) * 1.35;
        c = mix(c, lcd, u_color.w);
    }
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
