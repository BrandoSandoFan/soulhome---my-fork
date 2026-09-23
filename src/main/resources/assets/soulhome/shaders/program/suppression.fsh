#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;

// One suppressed player each. Target: xy is their centre on screen (0-1, bottom left), z the
// field's radius as a share of the screen's height, w how far it displaces the image. Shape: x is
// the observer's legibility (0 a formless smear, 1 crisp rings), y the ring count. A slot with no
// radius is empty and contributes nothing, which is what makes the pass the identity when nobody
// suppressed is in view.
uniform vec4 Target0;
uniform vec4 Target1;
uniform vec4 Target2;
uniform vec4 Target3;
uniform vec4 Shape0;
uniform vec4 Shape1;
uniform vec4 Shape2;
uniform vec4 Shape3;

in vec2 texCoord;

out vec4 fragColor;

vec2 warp(vec2 uv, vec4 target, vec4 shape) {
    if (target.z <= 0.0 || target.w <= 0.0) {
        return vec2(0.0);
    }

    float aspect = OutSize.x / OutSize.y;
    vec2 d = uv - target.xy;
    d.x *= aspect;

    float r = length(d) / target.z;

    if (r >= 1.0) {
        return vec2(0.0);
    }

    float falloff = 1.0 - smoothstep(0.0, 1.0, r);
    vec2 dir = r > 0.0001 ? normalize(d) : vec2(0.0);

    // legible: ripples running outward, as many as the rings the aura draws
    float rings = max(shape.y, 1.0);
    float ripple = sin(r * rings * 6.2831853 - Time * 2.0);

    // formless: the same energy with no structure in it, so nothing about it can be counted
    float noise = sin(uv.x * 37.0 + Time * 1.3) * cos(uv.y * 29.0 - Time * 1.7)
                + 0.5 * sin((uv.x + uv.y) * 53.0 + Time * 2.3);

    float wave = mix(noise, ripple, shape.x);
    vec2 push = dir * wave * target.w * 0.02 * falloff;
    vec2 swirl = vec2(-dir.y, dir.x) * (1.0 - shape.x) * target.w * 0.015 * falloff * sin(Time + r * 4.0);

    vec2 offset = push + swirl;
    offset.x /= aspect;
    return offset;
}

void main() {
    vec2 offset = warp(texCoord, Target0, Shape0)
                + warp(texCoord, Target1, Shape1)
                + warp(texCoord, Target2, Shape2)
                + warp(texCoord, Target3, Shape3);

    fragColor = texture(DiffuseSampler, clamp(texCoord + offset, vec2(0.0), vec2(1.0)));
}
