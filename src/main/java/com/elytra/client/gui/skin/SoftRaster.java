/*
 * Decompiled with CFR 0.152.
 */
package com.elytra.client.gui.skin;

import java.util.Arrays;

public final class SoftRaster {
    public final int w;
    public final int h;
    public final int ss;
    public final int W;
    public final int H;
    private final int[] hi;

    public SoftRaster(int w, int h, int ss) {
        this.w = w;
        this.h = h;
        this.ss = ss;
        this.W = w * ss;
        this.H = h * ss;
        this.hi = new int[this.W * this.H];
    }

    public void clear() {
        Arrays.fill(this.hi, 0);
    }

    public void poly(double[] xs, double[] ys, int rgb) {
        this.poly(xs, ys, rgb, false);
    }

    public void poly(double[] xs, double[] ys, int rgb, boolean erase) {
        int n = xs.length;
        double minY = 1.0E18;
        double maxY = -1.0E18;
        for (int i = 0; i < n; ++i) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        int y0 = Math.max(0, (int)Math.ceil(minY * (double)this.ss - 0.5));
        int y1 = Math.min(this.H - 1, (int)Math.ceil(maxY * (double)this.ss - 0.5) - 1);
        int col = erase ? 0 : 0xFF000000 | rgb & 0xFFFFFF;
        for (int y = y0; y <= y1; ++y) {
            double yc = ((double)y + 0.5) / (double)this.ss;
            double lo = 1.0E18;
            double hi2 = -1.0E18;
            for (int i = 0; i < n; ++i) {
                int j = (i + 1) % n;
                double ya = ys[i];
                double yb = ys[j];
                if (!(yc >= ya && yc < yb) && (!(yc >= yb) || !(yc < ya))) continue;
                double x = xs[i] + (yc - ya) / (yb - ya) * (xs[j] - xs[i]);
                if (x < lo) {
                    lo = x;
                }
                if (!(x > hi2)) continue;
                hi2 = x;
            }
            if (hi2 < lo) continue;
            int x0 = Math.max(0, (int)Math.ceil(lo * (double)this.ss - 0.5));
            int x1 = Math.min(this.W - 1, (int)Math.ceil(hi2 * (double)this.ss - 0.5) - 1);
            if (x1 < x0) continue;
            Arrays.fill(this.hi, y * this.W + x0, y * this.W + x1 + 1, col);
        }
    }

    public void line(double x0, double y0, double x1, double y1, double thick, int rgb) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-6) {
            return;
        }
        double ux = dx / len;
        double uy = dy / len;
        double hh = thick / 2.0;
        double ex = ux * hh;
        double ey = uy * hh;
        double nx = -uy * hh;
        double ny = ux * hh;
        this.poly(new double[]{x0 - ex + nx, x1 + ex + nx, x1 + ex - nx, x0 - ex - nx}, new double[]{y0 - ey + ny, y1 + ey + ny, y1 + ey - ny, y0 - ey - ny}, rgb);
    }

    public void resolve(int[] out) {
        int s2 = this.ss * this.ss;
        for (int y = 0; y < this.h; ++y) {
            for (int x = 0; x < this.w; ++x) {
                int cnt = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (int sy = 0; sy < this.ss; ++sy) {
                    int row = (y * this.ss + sy) * this.W + x * this.ss;
                    for (int sx = 0; sx < this.ss; ++sx) {
                        int c = this.hi[row + sx];
                        if (c >>> 24 == 0) continue;
                        ++cnt;
                        r += c >> 16 & 0xFF;
                        g += c >> 8 & 0xFF;
                        b += c & 0xFF;
                    }
                }
                if (cnt == 0) {
                    out[y * this.w + x] = 0;
                    continue;
                }
                int a = cnt * 255 / s2;
                out[y * this.w + x] = a << 24 | r / cnt << 16 | g / cnt << 8 | b / cnt;
            }
        }
    }
}


