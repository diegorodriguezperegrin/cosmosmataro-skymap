precision mediump float;

uniform sampler2D u_Texture;
varying float v_TexOffset;
varying mediump vec4 v_Color;

uniform float u_isNightMode; // 0.0 or 1.0

void main() {
    // Assuming 2 stars in the texture horizontally.
    // gl_PointCoord.x is 0..1. We map it to offset..offset+0.5
    vec2 uv = vec2(gl_PointCoord.x * 0.5 + v_TexOffset, gl_PointCoord.y);
    vec4 color = v_Color * texture2D(u_Texture, uv);
    if (u_isNightMode > 0.5) {
        float val = max(color.r, max(color.g, color.b));
        gl_FragColor = vec4(val, 0.0, 0.0, color.a);
    } else {
        gl_FragColor = color;
    }
}
