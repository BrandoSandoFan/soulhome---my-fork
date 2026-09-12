#version 150

uniform sampler2D DiffuseSampler;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

// Lifts dark pixels toward white in proportion to Strength - at 0 this is the identity, so an
// Observatory nobody has built yet changes nothing about how the game looks. Weighted toward the
// darkest pixels (1.0 - color) rather than a flat brightness add, so it reads as "you can see into
// the shadows now" rather than "the whole screen got paler".
void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    vec3 lifted = color.rgb + (1.0 - color.rgb) * Strength * 0.6;

    fragColor = vec4(lifted, color.a);
}
