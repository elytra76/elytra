/*
 * Decompiled with CFR 0.152.
 */
package com.elytra.client.gui.skin;


import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;

public final class SkinModel {
    private static final double LX = -0.35;
    private static final double LY = 0.65;
    private static final double LZ = 0.7;

    private SkinModel() {
    }

    private static double[] rotX(double[] v, double py, double pz, double a) {
        double c = Math.cos(a);
        double sn = Math.sin(a);
        double y = v[1] - py;
        double z = v[2] - pz;
        return new double[]{v[0], py + y * c - z * sn, pz + y * sn + z * c};
    }

    private static double[] rotY(double[] v, double px, double pz, double a) {
        double c = Math.cos(a);
        double sn = Math.sin(a);
        double x = v[0] - px;
        double z = v[2] - pz;
        return new double[]{px + x * c + z * sn, v[1], pz - x * sn + z * c};
    }

    private static double smooth(double v, double a, double b) {
        double t = Math.max(0.0, Math.min(1.0, (v - a) / (b - a)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static void box(SoftRaster r, BufferedImage im, Xf xf, double ox, double oy, double rs, double x0, double y0, double z0, double w, double h, double d, double e, int tu, int tv, boolean mirror) {
        double[] dArray;
        double[] dArray2;
        int iw = (int)Math.round(w);
        int ih = (int)Math.round(h);
        int id = (int)Math.round(d);
        double kx = (w + 2.0 * e) / w;
        double ky = (h + 2.0 * e) / h;
        double kz = (d + 2.0 * e) / d;
        double xa = x0 - e;
        double xb = x0 + w + e;
        double ya = y0 - e;
        double yb = y0 + h + e;
        double za = z0 - e;
        double zb = z0 + d + e;
        double[][] dArrayArray = new double[6][];
        dArrayArray[0] = new double[]{xa, yb, zb, kx, 0.0, 0.0, 0.0, -ky, 0.0, 0.0, 0.0, 1.0, tu + id, tv + id, iw, ih, mirror ? 1.0 : 0.0};
        dArrayArray[1] = new double[]{xb, yb, za, -kx, 0.0, 0.0, 0.0, -ky, 0.0, 0.0, 0.0, -1.0, tu + 2 * id + iw, tv + id, iw, ih, mirror ? 1.0 : 0.0};
        if (mirror) {
            double[] dArray3 = new double[17];
            dArray3[0] = xa;
            dArray3[1] = yb;
            dArray3[2] = zb;
            dArray3[3] = 0.0;
            dArray3[4] = 0.0;
            dArray3[5] = -kz;
            dArray3[6] = 0.0;
            dArray3[7] = -ky;
            dArray3[8] = 0.0;
            dArray3[9] = -1.0;
            dArray3[10] = 0.0;
            dArray3[11] = 0.0;
            dArray3[12] = tu + id + iw;
            dArray3[13] = tv + id;
            dArray3[14] = id;
            dArray3[15] = ih;
            dArray2 = dArray3;
            dArray3[16] = 0.0;
        } else {
            double[] dArray4 = new double[17];
            dArray4[0] = xa;
            dArray4[1] = yb;
            dArray4[2] = za;
            dArray4[3] = 0.0;
            dArray4[4] = 0.0;
            dArray4[5] = kz;
            dArray4[6] = 0.0;
            dArray4[7] = -ky;
            dArray4[8] = 0.0;
            dArray4[9] = -1.0;
            dArray4[10] = 0.0;
            dArray4[11] = 0.0;
            dArray4[12] = tu;
            dArray4[13] = tv + id;
            dArray4[14] = id;
            dArray4[15] = ih;
            dArray2 = dArray4;
            dArray4[16] = 0.0;
        }
        dArrayArray[2] = dArray2;
        if (mirror) {
            double[] dArray5 = new double[17];
            dArray5[0] = xb;
            dArray5[1] = yb;
            dArray5[2] = za;
            dArray5[3] = 0.0;
            dArray5[4] = 0.0;
            dArray5[5] = kz;
            dArray5[6] = 0.0;
            dArray5[7] = -ky;
            dArray5[8] = 0.0;
            dArray5[9] = 1.0;
            dArray5[10] = 0.0;
            dArray5[11] = 0.0;
            dArray5[12] = tu;
            dArray5[13] = tv + id;
            dArray5[14] = id;
            dArray5[15] = ih;
            dArray = dArray5;
            dArray5[16] = 0.0;
        } else {
            double[] dArray6 = new double[17];
            dArray6[0] = xb;
            dArray6[1] = yb;
            dArray6[2] = zb;
            dArray6[3] = 0.0;
            dArray6[4] = 0.0;
            dArray6[5] = -kz;
            dArray6[6] = 0.0;
            dArray6[7] = -ky;
            dArray6[8] = 0.0;
            dArray6[9] = 1.0;
            dArray6[10] = 0.0;
            dArray6[11] = 0.0;
            dArray6[12] = tu + id + iw;
            dArray6[13] = tv + id;
            dArray6[14] = id;
            dArray6[15] = ih;
            dArray = dArray6;
            dArray6[16] = 0.0;
        }
        dArrayArray[3] = dArray;
        dArrayArray[4] = new double[]{xa, yb, za, kx, 0.0, 0.0, 0.0, 0.0, kz, 0.0, 1.0, 0.0, tu + id, tv, iw, id, mirror ? 1.0 : 0.0};
        dArrayArray[5] = new double[]{xa, ya, zb, kx, 0.0, 0.0, 0.0, 0.0, -kz, 0.0, -1.0, 0.0, tu + id + iw, tv, iw, id, mirror ? 1.0 : 0.0};
        double[][] faces = dArrayArray;
        double[] xs = new double[4];
        double[] ys = new double[4];
        int[] order = new int[]{0, 1, 3, 2};
        for (double[] f : faces) {
            double[] n = xf.dir(f[9], f[10], f[11]);
            if (n[2] <= 0.02) continue;
            double shade = 0.62 + 0.38 * Math.max(0.0, n[0] * -0.35 + n[1] * 0.65 + n[2] * 0.7);
            int cols = (int)f[14];
            int rows = (int)f[15];
            boolean flip = f[16] > 0.0;
            for (int j = 0; j < rows; ++j) {
                for (int i = 0; i < cols; ++i) {
                    int argb;
                    int col = flip ? cols - 1 - i : i;
                    int px = (int)f[12] + col;
                    int py = (int)f[13] + j;
                    if (px < 0 || py < 0 || px >= im.getWidth() || py >= im.getHeight() || (argb = im.getRGB(px, py)) >>> 24 < 40) continue;
                    for (int c = 0; c < 4; ++c) {
                        int k = order[c];
                        double ui = i + (k & 1);
                        double vj = j + (k >> 1);
                        double[] v = xf.pt(f[0] + f[3] * ui + f[6] * vj, f[1] + f[4] * ui + f[7] * vj, f[2] + f[5] * ui + f[8] * vj);
                        double persp = 1.0 / (1.0 - v[2] / 260.0);
                        xs[c] = ox + v[0] * persp * rs;
                        ys[c] = oy - (16.0 + (v[1] - 16.0) * persp) * rs;
                    }
                    int rr = (int)Math.min(255.0, (double)(argb >> 16 & 0xFF) * shade);
                    int gg = (int)Math.min(255.0, (double)(argb >> 8 & 0xFF) * shade);
                    int bb = (int)Math.min(255.0, (double)(argb & 0xFF) * shade);
                    r.poly(xs, ys, rr << 16 | gg << 8 | bb);
                }
            }
        }
    }

    public static void render(SoftRaster r, BufferedImage im, boolean slim, double rs, double ox, double oy, double t, double p, double dxm, double dym) {
        boolean modern = im.getHeight() >= 64;
        int aw = slim ? 3 : 4;
        double ease = SkinModel.smooth(p, 0.7, 1.0);
        double amp = 1.0 - ease;
        double ph = t * 9.0;
        double swing = Math.sin(ph) * 0.75 * amp + Math.sin(t * 1.7) * 0.05 * (1.0 - amp);
        double lift = Math.abs(Math.sin(ph)) * 0.5 * amp;
        double bodyYaw = -1.35 * amp + dxm * 0.55 * ease;
        double headYaw = dxm * 0.45 * ease;
        double headPitch = dym * 0.5 * ease;
        Xf head = new Xf();
        Xf body = new Xf();
        Xf rArm = new Xf();
        Xf lArm = new Xf();
        Xf rLeg = new Xf();
        Xf lLeg = new Xf();
        head.kind = 1;
        for (Xf x : new Xf[]{rArm, lArm, rLeg, lLeg}) {
            x.kind = 2;
        }
        lArm.pivY = 22.0;
        rArm.pivY = 22.0;
        lLeg.pivY = 12.0;
        rLeg.pivY = 12.0;
        rArm.swing = -swing;
        lArm.swing = swing;
        rLeg.swing = swing;
        lLeg.swing = -swing;
        head.headYaw = headYaw;
        head.headPitch = headPitch;
        for (Xf x : new Xf[]{head, body, rArm, lArm, rLeg, lLeg}) {
            x.bodyYaw = bodyYaw;
            x.lift = lift;
        }
        ArrayList<Part> parts = new ArrayList<Part>();
        parts.add(new Part(head, -4.0, 24.0, -4.0, 8.0, 8.0, 8.0, 0, 0, 32, 0, false, true));
        parts.add(new Part(body, -4.0, 12.0, -2.0, 8.0, 12.0, 4.0, 16, 16, 16, 32, false, modern));
        parts.add(new Part(rArm, -4 - aw, 12.0, -2.0, aw, 12.0, 4.0, 40, 16, 40, 32, false, modern));
        parts.add(new Part(lArm, 4.0, 12.0, -2.0, aw, 12.0, 4.0, modern ? 32 : 40, modern ? 48 : 16, 48, 48, !modern, modern));
        parts.add(new Part(rLeg, -4.0, 0.0, -2.0, 4.0, 12.0, 4.0, 0, 16, 0, 32, false, modern));
        parts.add(new Part(lLeg, 0.0, 0.0, -2.0, 4.0, 12.0, 4.0, modern ? 16 : 0, modern ? 48 : 16, 0, 48, !modern, modern));
        for (Part pt : parts) {
            pt.depth = pt.xf.pt(pt.x0 + pt.w / 2.0, pt.y0 + pt.h / 2.0, pt.z0 + pt.d / 2.0)[2];
        }
        parts.sort(Comparator.comparingDouble(a -> a.depth));
        r.clear();
        for (Part pt : parts) {
            SkinModel.box(r, im, pt.xf, ox, oy, rs, pt.x0, pt.y0, pt.z0, pt.w, pt.h, pt.d, 0.0, pt.tu, pt.tv, pt.mirror);
            if (!pt.overlay) continue;
            SkinModel.box(r, im, pt.xf, ox, oy, rs, pt.x0, pt.y0, pt.z0, pt.w, pt.h, pt.d, 0.5, pt.otu, pt.otv, false);
        }
    }

    private static final class Xf {
        int kind;
        double bodyYaw;
        double headYaw;
        double headPitch;
        double swing;
        double pivY;
        double pivZ;
        double lift;

        private Xf() {
        }

        double[] pt(double x, double y, double z) {
            double[] v = new double[]{x, y, z};
            if (this.kind == 1) {
                v = SkinModel.rotY(v, 0.0, 0.0, this.headYaw);
                v = SkinModel.rotX(v, 24.0, 0.0, this.headPitch);
            } else if (this.kind == 2) {
                v = SkinModel.rotX(v, this.pivY, this.pivZ, this.swing);
            }
            v = SkinModel.rotY(v, 0.0, 0.0, this.bodyYaw);
            v[1] = v[1] + this.lift;
            return v;
        }

        double[] dir(double x, double y, double z) {
            double[] v = new double[]{x, y, z};
            if (this.kind == 1) {
                v = SkinModel.rotY(v, 0.0, 0.0, this.headYaw);
                v = SkinModel.rotX(v, 0.0, 0.0, this.headPitch);
            } else if (this.kind == 2) {
                v = SkinModel.rotX(v, 0.0, 0.0, this.swing);
            }
            return SkinModel.rotY(v, 0.0, 0.0, this.bodyYaw);
        }
    }

    private static final class Part {
        final Xf xf;
        final double x0;
        final double y0;
        final double z0;
        final double w;
        final double h;
        final double d;
        final int tu;
        final int tv;
        final int otu;
        final int otv;
        final boolean mirror;
        final boolean overlay;
        double depth;

        Part(Xf xf, double x0, double y0, double z0, double w, double h, double d, int tu, int tv, int otu, int otv, boolean mirror, boolean overlay) {
            this.xf = xf;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.w = w;
            this.h = h;
            this.d = d;
            this.tu = tu;
            this.tv = tv;
            this.otu = otu;
            this.otv = otv;
            this.mirror = mirror;
            this.overlay = overlay;
        }
    }
}


