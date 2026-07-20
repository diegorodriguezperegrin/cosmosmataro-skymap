precision mediump float;

varying vec4 v_Color;
varying vec2 v_TexCoord;

uniform sampler2D u_Texture;

uniform float u_isNightMode;

void main() {
    // Multiply texture color (alpha mainly) by vertex color
    vec4 color = v_Color * texture2D(u_Texture, v_TexCoord);
    if (u_isNightMode > 0.5) {
        float val = max(color.r, max(color.g, color.b));
        gl_FragColor = vec4(val, 0.0, 0.0, color.a);
    } else {
        gl_FragColor = color;
    }
}
