// Ported from Jakub Antalik's Border Beam (MIT, https://github.com/Jakubantalik/Libraries.dev,
// packages/border-beam); see liquid/NOTICE.md. The shader is the React Native port's rotate-family
// SkSL in AGSL, extended to draw into a canvas larger than the element (for the bloom's spill);
// palettes and tunings are the library's "md" size, "colorful" variant.
package dev.southstar21.kaloria.core.designsystem.component

/**
 * One beam layer: a stack of radial colour blobs in a rounded-rect geometry (border ring, filled
 * inner glow, or bloom ring), windowed by the rotating conic mask, with the conic highlight on top
 * and the CSS filter matrix applied. The element is [uRectSize], centred in a canvas of [uSize].
 */
internal val BeamShader = """
const float PI = 3.14159265358979;

uniform float2 uSize;
uniform float2 uRectSize;
uniform float uAngle;
uniform float uRadius;
uniform float uBorderWidth;
uniform float uKind;
uniform float uEdgeMaskPx;
uniform float uBlobs[${MaxBlobs * 8}];
uniform float uBlobCount;
uniform float uBg[${MaxBgStops * 2}];
uniform float uBgCount;
uniform float uBgIsBlack;
uniform float uMask[${MaxMaskStops * 2}];
uniform float uMaskCount;
uniform float uCM[9];
uniform float uOpacity;
uniform float4 uShadowColor;
uniform float uShadowBlur;

float roundedRectSDF(float2 p, float2 halfSize, float radius) {
  float2 q = abs(p) - halfSize + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float conicFraction(float2 p, float2 center) {
  float2 d = p - center;
  float theta = atan(d.x, -d.y);
  return fract(theta / (2.0 * PI) + 1.0);
}

float bgStopsAlpha(float t) {
  int n = int(uBgCount) / 2;
  if (n == 0) { return 1.0; }
  float result = uBg[1];
  for (int i = 1; i < $MaxBgStops; i++) {
    if (i < n) {
      float p0 = uBg[2 * (i - 1)];
      float a0 = uBg[2 * (i - 1) + 1];
      float p1 = uBg[2 * i];
      float a1 = uBg[2 * i + 1];
      if (t > p0) {
        float f = p1 > p0 ? clamp((t - p0) / (p1 - p0), 0.0, 1.0) : 1.0;
        result = mix(a0, a1, f);
      }
    }
  }
  return result;
}

float maskStopsAlpha(float t) {
  int n = int(uMaskCount) / 2;
  if (n == 0) { return 1.0; }
  float result = uMask[1];
  for (int i = 1; i < $MaxMaskStops; i++) {
    if (i < n) {
      float p0 = uMask[2 * (i - 1)];
      float a0 = uMask[2 * (i - 1) + 1];
      float p1 = uMask[2 * i];
      float a1 = uMask[2 * i + 1];
      if (t > p0) {
        float f = p1 > p0 ? clamp((t - p0) / (p1 - p0), 0.0, 1.0) : 1.0;
        result = mix(a0, a1, f);
      }
    }
  }
  return result;
}

float4 srcOver(float4 src, float4 dst) {
  return src + dst * (1.0 - src.a);
}

half4 main(float2 position) {
  float2 center = uSize * 0.5;
  float2 origin = center - uRectSize * 0.5;
  float2 rel = position - center;
  float2 local = position - origin;
  int kind = int(uKind);

  float outerSDF = roundedRectSDF(rel, uRectSize * 0.5, uRadius);
  float aa = 1.0;
  float outerCov = 1.0 - smoothstep(-aa, 0.0, outerSDF);
  float geom;
  if (kind == 1) {
    geom = outerCov;
  } else {
    float innerRadius = max(uRadius - uBorderWidth, 0.0);
    float innerSDF = roundedRectSDF(rel, uRectSize * 0.5 - uBorderWidth, innerRadius);
    float innerCov = 1.0 - smoothstep(-aa, 0.0, innerSDF);
    geom = outerCov - innerCov;
  }
  if (geom <= 0.0) { return half4(0.0); }

  float mask = 1.0;
  if (uMaskCount > 0.5) {
    float t = fract(conicFraction(position, center) - uAngle);
    mask = maskStopsAlpha(t);
  }

  if (kind == 1 && uEdgeMaskPx > 0.0) {
    float ev = max(1.0 - local.y / uEdgeMaskPx, 1.0 - (uRectSize.y - local.y) / uEdgeMaskPx);
    float eh = max(1.0 - local.x / uEdgeMaskPx, 1.0 - (uRectSize.x - local.x) / uEdgeMaskPx);
    mask *= clamp(max(ev, 0.0) + max(eh, 0.0), 0.0, 1.0);
  }
  if (mask <= 0.001) { return half4(0.0); }

  float4 acc = float4(0.0);
  int nBlobs = int(uBlobCount);
  for (int i = ${MaxBlobs - 1}; i >= 0; i--) {
    if (i < nBlobs) {
      float rx = max(uBlobs[i * 8 + 0], 0.001);
      float ry = max(uBlobs[i * 8 + 1], 0.001);
      float2 c = origin + float2(uBlobs[i * 8 + 2], uBlobs[i * 8 + 3]) * uRectSize;
      float d = length((position - c) / float2(rx, ry));
      float alpha = uBlobs[i * 8 + 7] * clamp(1.0 - d, 0.0, 1.0);
      if (alpha > 0.0) {
        float3 rgb = float3(uBlobs[i * 8 + 4], uBlobs[i * 8 + 5], uBlobs[i * 8 + 6]);
        acc = srcOver(float4(rgb * alpha, alpha), acc);
      }
    }
  }

  if (uBgCount > 0.5) {
    float t = fract(conicFraction(position, center) - uAngle);
    float a = bgStopsAlpha(t);
    float3 rgb = uBgIsBlack > 0.5 ? float3(0.0) : float3(1.0);
    acc = srcOver(float4(rgb * a, a), acc);
  }

  if (kind == 1 && uShadowColor.a > 0.0 && uShadowBlur > 0.0) {
    float depth = -outerSDF;
    float fall = clamp(1.0 - depth / (uShadowBlur + 1.0), 0.0, 1.0);
    float a = uShadowColor.a * fall * fall;
    acc = srcOver(float4(uShadowColor.rgb * a, a), acc);
  }

  if (acc.a > 0.0001) {
    float3 rgb = acc.rgb / acc.a;
    float3 outRGB = float3(
      dot(float3(uCM[0], uCM[1], uCM[2]), rgb),
      dot(float3(uCM[3], uCM[4], uCM[5]), rgb),
      dot(float3(uCM[6], uCM[7], uCM[8]), rgb)
    );
    acc.rgb = clamp(outRGB, 0.0, 1.0) * acc.a;
  }

  float finalA = acc.a * geom * mask * clamp(uOpacity, 0.0, 1.0);
  if (acc.a > 0.0001) {
    return half4(half3(acc.rgb / acc.a * finalA), half(finalA));
  }
  return half4(0.0);
}
"""
