// libhoneycrisp: CoreText text, SF Symbols, continuous-corner shapes and system appearance for dev.honeycrisp.mac.MacNative.
// Everything renders white, premultiplied RGBA8 (top row first) into private CGBitmapContexts, so it is safe on any
// thread and needs no NSApplication.
#import <AppKit/AppKit.h>
#import <CoreText/CoreText.h>
#import <CoreText/SFNTLayoutTypes.h>
#include <jni.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

static NSFontWeight fontWeight(int w) {
	static const double t[] = {-0.8, -0.6, -0.4, 0, 0.23, 0.3, 0.4, 0.56, 0.62};
	double f = (w - 100) / 100.0;
	if (f <= 0) return t[0];
	if (f >= 8) return t[8];
	int i = (int)f;
	return t[i] + (t[i + 1] - t[i]) * (f - i);
}

static NSFont *systemFont(float px, int weight, jboolean mono) {
	NSFontWeight w = fontWeight(weight);
	if (mono) return [NSFont monospacedSystemFontOfSize:px weight:w];
	NSFont *base = [NSFont systemFontOfSize:px weight:w];
	NSFontDescriptor *desc = [base.fontDescriptor fontDescriptorByAddingAttributes:@{
		NSFontFeatureSettingsAttribute: @[@{
			NSFontFeatureTypeIdentifierKey: @(kNumberSpacingType),
			NSFontFeatureSelectorIdentifierKey: @(kMonospacedNumbersSelector)
		}]
	}];
	NSFont *tabular = [NSFont fontWithDescriptor:desc size:px];
	return tabular ? tabular : base;
}

static NSString *jstr(JNIEnv *env, jstring s) {
	const jchar *c = (*env)->GetStringChars(env, s, NULL);
	NSString *r = [NSString stringWithCharacters:c length:(NSUInteger)(*env)->GetStringLength(env, s)];
	(*env)->ReleaseStringChars(env, s, c);
	return r;
}

static CTLineRef makeLine(NSString *text, NSFont *font) {
	NSDictionary *attrs = @{(__bridge id)kCTFontAttributeName: font, (__bridge id)kCTForegroundColorFromContextAttributeName: @YES};
	NSAttributedString *as = [[NSAttributedString alloc] initWithString:text attributes:attrs];
	return CTLineCreateWithAttributedString((__bridge CFAttributedStringRef)as);
}

static CGContextRef bitmap(int w, int h) {
	CGColorSpaceRef cs = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
	CGContextRef ctx = CGBitmapContextCreate(NULL, (size_t)w, (size_t)h, 8, (size_t)w * 4, cs,
		kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
	CGColorSpaceRelease(cs);
	CGContextSetAllowsAntialiasing(ctx, true);
	CGContextSetShouldAntialias(ctx, true);
	CGContextSetRGBFillColor(ctx, 1, 1, 1, 1);
	return ctx;
}

static jbyteArray pixels(JNIEnv *env, CGContextRef ctx) {
	int w = (int)CGBitmapContextGetWidth(ctx), h = (int)CGBitmapContextGetHeight(ctx);
	jbyteArray a = (*env)->NewByteArray(env, w * h * 4);
	if (a) (*env)->SetByteArrayRegion(env, a, 0, w * h * 4, (const jbyte *)CGBitmapContextGetData(ctx));
	return a;
}

static void putInts(JNIEnv *env, jintArray out, int a, int b, int c, int d) {
	jint v[4] = {a, b, c, d};
	(*env)->SetIntArrayRegion(env, out, 0, 4, v);
}

// {advance width, ascent, descent, leading} in pixels for a font of pixelSize.
JNIEXPORT jfloatArray JNICALL Java_dev_honeycrisp_mac_MacNative_measureText(JNIEnv *env, jclass cls, jstring text, jfloat px, jint weight, jboolean mono) {
	@autoreleasepool {
		NSFont *font = systemFont(px, weight, mono);
		CTLineRef line = makeLine(jstr(env, text), font);
		jfloat v[4] = {(jfloat)CTLineGetTypographicBounds(line, NULL, NULL, NULL), (jfloat)font.ascender, (jfloat)-font.descender, (jfloat)font.leading};
		CFRelease(line);
		jfloatArray a = (*env)->NewFloatArray(env, 4);
		(*env)->SetFloatArrayRegion(env, a, 0, 4, v);
		return a;
	}
}

// White glyphs of one line; out = {width, height, pen x, baseline row from top}. null when nothing is inked.
JNIEXPORT jbyteArray JNICALL Java_dev_honeycrisp_mac_MacNative_renderText(JNIEnv *env, jclass cls, jstring text, jfloat px, jint weight, jboolean mono, jintArray out) {
	@autoreleasepool {
		CTLineRef line = makeLine(jstr(env, text), systemFont(px, weight, mono));
		CGRect ink = CTLineGetBoundsWithOptions(line, kCTLineBoundsUseGlyphPathBounds);
		if (CGRectIsEmpty(ink) || CGRectIsNull(ink)) {
			CFRelease(line);
			return NULL;
		}
		int x0 = (int)floor(CGRectGetMinX(ink)) - 1, x1 = (int)ceil(CGRectGetMaxX(ink)) + 1;
		int y0 = (int)floor(CGRectGetMinY(ink)) - 1, y1 = (int)ceil(CGRectGetMaxY(ink)) + 1;
		CGContextRef ctx = bitmap(x1 - x0, y1 - y0);
		CGContextSetShouldSmoothFonts(ctx, false);
		CGContextSetShouldSubpixelPositionFonts(ctx, false);
		CGContextSetAllowsFontSubpixelQuantization(ctx, false);
		CGContextSetTextPosition(ctx, -x0, -y0);
		CTLineDraw(line, ctx);
		jbyteArray a = pixels(env, ctx);
		putInts(env, out, x1 - x0, y1 - y0, -x0, y1);
		CGContextRelease(ctx);
		CFRelease(line);
		return a;
	}
}

// SF Symbol (template, white) centered in a box×box bitmap; falls back to questionmark.square.dashed when the symbol does not exist.
JNIEXPORT jbyteArray JNICALL Java_dev_honeycrisp_mac_MacNative_renderSymbol(JNIEnv *env, jclass cls, jstring name, jint box, jfloat pointSize, jint weight) {
	@autoreleasepool {
		if (box <= 0 || !name) return NULL;
		NSString *sym = jstr(env, name);
		NSImage *img = [NSImage imageWithSystemSymbolName:sym accessibilityDescription:nil];
		if (!img) {
			static NSMutableSet<NSString *> *missing = nil;
			static dispatch_once_t once;
			dispatch_once(&once, ^{ missing = [NSMutableSet new]; });
			@synchronized (missing) {
				if (![missing containsObject:sym]) {
					[missing addObject:sym];
					fprintf(stderr, "[Honeycrisp Client] missing SF Symbol: %s\n", sym.UTF8String);
				}
			}
			img = [NSImage imageWithSystemSymbolName:@"questionmark.square.dashed" accessibilityDescription:nil];
		}
		if (!img) return NULL;
		img = [img imageWithSymbolConfiguration:[NSImageSymbolConfiguration configurationWithPointSize:pointSize weight:fontWeight(weight) scale:NSImageSymbolScaleMedium]];
		NSSize s = img.size;
		if (s.width <= 0 || s.height <= 0) return NULL;
		double k = fmin(1.0, fmin(box / s.width, box / s.height));
		double w = s.width * k, h = s.height * k;
		CGContextRef ctx = bitmap(box, box);
		NSGraphicsContext *g = [NSGraphicsContext graphicsContextWithCGContext:ctx flipped:NO];
		[NSGraphicsContext saveGraphicsState];
		[NSGraphicsContext setCurrentContext:g];
		[img drawInRect:NSMakeRect(round((box - w) / 2), round((box - h) / 2), w, h) fromRect:NSZeroRect operation:NSCompositingOperationSourceOver fraction:1];
		[NSGraphicsContext restoreGraphicsState];
		CGContextSetBlendMode(ctx, kCGBlendModeSourceIn);
		CGContextFillRect(ctx, CGRectMake(0, 0, box, box));
		jbyteArray a = pixels(env, ctx);
		CGContextRelease(ctx);
		return a;
	}
}

// Continuous ("squircle") rounded rect after Figma's corner-smoothing construction (smoothing 0.6 ≈ Apple's).
static void addContinuousRect(CGMutablePathRef path, double x, double y, double w, double h, double radius) {
	double budget = fmin(w, h) / 2, r = fmin(radius, budget);
	if (r <= 0) {
		CGPathAddRect(path, NULL, CGRectMake(x, y, w, h));
		return;
	}
	double s = fmin(0.6, fmax(0, budget / r - 1)), p = fmin((1 + s) * r, budget);
	double arcDeg = 90 * (1 - s), arcLen = sin(arcDeg / 2 * M_PI / 180) * r * M_SQRT2;
	double alpha = (90 - arcDeg) / 2, beta = 45 * s;
	double c = r * tan(alpha / 2 * M_PI / 180) * cos(beta * M_PI / 180), d = c * tan(beta * M_PI / 180);
	double b = (p - arcLen - c - d) / 3, a = 2 * b;
	double kArc = 4.0 / 3.0 * tan(arcDeg * M_PI / 180 / 4) * r, tl = hypot(c, d);
	// corners in path order with the direction of the edge arriving at them (u) and leaving them (v)
	double cx[4] = {x + w, x + w, x, x}, cy[4] = {y, y + h, y + h, y};
	double ux[4] = {1, 0, -1, 0}, uy[4] = {0, 1, 0, -1};
	for (int i = 0; i < 4; i++) {
		double Ux = ux[i], Uy = uy[i], Vx = ux[(i + 1) % 4], Vy = uy[(i + 1) % 4];
#define P(du, dv) (sx + (du) * Ux + (dv) * Vx), (sy + (du) * Uy + (dv) * Vy)
		double sx = cx[i] - p * Ux, sy = cy[i] - p * Uy;
		if (i == 0) CGPathMoveToPoint(path, NULL, sx, sy);
		else CGPathAddLineToPoint(path, NULL, sx, sy);
		CGPathAddCurveToPoint(path, NULL, P(a, 0), P(a + b, 0), P(a + b + c, d));
		double e = a + b + c + arcLen, f = d + arcLen;
		if (tl > 0) CGPathAddCurveToPoint(path, NULL, P(a + b + c + kArc * c / tl, d + kArc * d / tl), P(e - kArc * d / tl, f - kArc * c / tl), P(e, f));
		else CGPathAddCurveToPoint(path, NULL, P(a + b + c + kArc, d), P(e, f - kArc), P(e, f));
		CGPathAddCurveToPoint(path, NULL, P(e + d, f + c), P(e + d, f + b + c), P(e + d, f + a + b + c));
#undef P
	}
	CGPathCloseSubpath(path);
}

// Three box passes ≈ Gaussian of sigma (alpha plane, zero outside).
static void blurAlpha(float *img, int w, int h, float sigma) {
	if (sigma <= 0) return;
	double ideal = sqrt(12.0 * sigma * sigma / 3 + 1);
	int wl = (int)floor(ideal);
	if (wl % 2 == 0) wl--;
	int m = (int)round((12.0 * sigma * sigma - 3.0 * wl * wl - 12.0 * wl - 9) / (-4.0 * wl - 4));
	int n = w > h ? w : h;
	float *line = malloc(sizeof(float) * (size_t)(n + 1)), *tmp = malloc(sizeof(float) * (size_t)n);
	for (int pass = 0; pass < 3; pass++) {
		int rad = ((pass < m ? wl : wl + 2) - 1) / 2;
		if (rad <= 0) continue;
		float inv = 1.0f / (2 * rad + 1);
		for (int dir = 0; dir < 2; dir++) {
			int len = dir ? h : w, count = dir ? w : h, stride = dir ? w : 1, step = dir ? 1 : w;
			for (int j = 0; j < count; j++) {
				float *base = img + (size_t)j * step;
				line[0] = 0;
				for (int i = 0; i < len; i++) line[i + 1] = line[i] + base[(size_t)i * stride];
				for (int i = 0; i < len; i++) {
					int lo = i - rad < 0 ? 0 : i - rad, hi = i + rad + 1 > len ? len : i + rad + 1;
					tmp[i] = (line[hi] - line[lo]) * inv;
				}
				for (int i = 0; i < len; i++) base[(size_t)i * stride] = tmp[i];
			}
		}
	}
	free(line);
	free(tmp);
}

// A w×h continuous rounded rect at (margin, margin) in a (w+2m)×(h+2m) bitmap: filled, or an inside stroke when
// stroke > 0, then blurred by sigma (pixels).
JNIEXPORT jbyteArray JNICALL Java_dev_honeycrisp_mac_MacNative_renderRoundRect(JNIEnv *env, jclass cls, jint w, jint h, jfloat radius, jfloat stroke, jfloat sigma, jint margin) {
	@autoreleasepool {
		int tw = w + 2 * margin, th = h + 2 * margin;
		if (w <= 0 || h <= 0) return NULL;
		CGContextRef ctx = bitmap(tw, th);
		CGMutablePathRef path = CGPathCreateMutable();
		addContinuousRect(path, margin, margin, w, h, radius);
		if (stroke > 0 && stroke * 2 < fmin(w, h)) {
			addContinuousRect(path, margin + stroke, margin + stroke, w - 2 * stroke, h - 2 * stroke, fmax(0, radius - stroke));
			CGContextAddPath(ctx, path);
			CGContextEOFillPath(ctx);
		} else {
			CGContextAddPath(ctx, path);
			CGContextFillPath(ctx);
		}
		CGPathRelease(path);
		uint8_t *px = CGBitmapContextGetData(ctx);
		if (sigma > 0) {
			size_t n = (size_t)tw * th;
			float *alpha = malloc(sizeof(float) * n);
			for (size_t i = 0; i < n; i++) alpha[i] = px[i * 4 + 3];
			blurAlpha(alpha, tw, th, sigma);
			for (size_t i = 0; i < n; i++) {
				int v = (int)lrintf(alpha[i]);
				uint8_t b = (uint8_t)(v < 0 ? 0 : v > 255 ? 255 : v);
				px[i * 4] = px[i * 4 + 1] = px[i * 4 + 2] = px[i * 4 + 3] = b;
			}
			free(alpha);
		}
		jbyteArray a = pixels(env, ctx);
		CGContextRelease(ctx);
		return a;
	}
}

static BOOL isDark(void) {
	NSString *style = [[NSUserDefaults standardUserDefaults] stringForKey:@"AppleInterfaceStyle"];
	return [style caseInsensitiveCompare:@"Dark"] == NSOrderedSame;
}

JNIEXPORT jboolean JNICALL Java_dev_honeycrisp_mac_MacNative_darkAppearance(JNIEnv *env, jclass cls) {
	@autoreleasepool {
		return isDark() ? JNI_TRUE : JNI_FALSE;
	}
}

JNIEXPORT jint JNICALL Java_dev_honeycrisp_mac_MacNative_accentColor(JNIEnv *env, jclass cls) {
	@autoreleasepool {
		__block NSColor *c = nil;
		NSAppearance *app = [NSAppearance appearanceNamed:isDark() ? NSAppearanceNameDarkAqua : NSAppearanceNameAqua];
		[app performAsCurrentDrawingAppearance:^{
			c = [[NSColor controlAccentColor] colorUsingColorSpace:[NSColorSpace sRGBColorSpace]];
		}];
		if (!c) return (jint)0xFF007AFF;
		int r = (int)lround(fmin(1, fmax(0, c.redComponent)) * 255), g = (int)lround(fmin(1, fmax(0, c.greenComponent)) * 255);
		int b = (int)lround(fmin(1, fmax(0, c.blueComponent)) * 255);
		return (jint)(0xFF000000u | (unsigned)(r << 16) | (unsigned)(g << 8) | (unsigned)b);
	}
}

JNIEXPORT jdouble JNICALL Java_dev_honeycrisp_mac_MacNative_backingScale(JNIEnv *env, jclass cls) {
	CGDisplayModeRef m = CGDisplayCopyDisplayMode(CGMainDisplayID());
	if (!m) return 2.0;
	double s = CGDisplayModeGetWidth(m) > 0 ? (double)CGDisplayModeGetPixelWidth(m) / (double)CGDisplayModeGetWidth(m) : 2.0;
	CGDisplayModeRelease(m);
	return s;
}
