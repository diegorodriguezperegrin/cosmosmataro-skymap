attribute vec4 a_Position;
attribute vec2 a_TexCoord;

uniform mat4 u_MVPMatrix;

varying vec2 v_TexCoord;

void main() {
    v_TexCoord = a_TexCoord;
    gl_Position = u_MVPMatrix * a_Position;
}
