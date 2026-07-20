attribute vec4 a_Position;
attribute float a_PointSize;
attribute float a_TexOffset;

uniform mat4 u_MVPMatrix;

attribute vec4 a_Color;
varying mediump vec4 v_Color;
varying float v_TexOffset;

void main() {
    gl_Position = u_MVPMatrix * a_Position;
    gl_PointSize = max(a_PointSize * 3.0 + 5.0, 5.0);
    v_TexOffset = a_TexOffset;
    v_Color = a_Color;
}
