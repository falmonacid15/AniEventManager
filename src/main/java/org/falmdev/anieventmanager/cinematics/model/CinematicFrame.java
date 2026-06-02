package org.falmdev.anieventmanager.cinematics.model;

/**
 * Un frame grabado — la posición exacta del admin en un tick específico.
 *
 * {@code cut} indica que este frame es el primero después de una pausa:
 * el reproductor hace un teleport directo en lugar de movimiento suave,
 * produciendo el efecto de "corte de escena" de EtherCinematic.
 */
public class CinematicFrame {

    private final double x, y, z;
    private final float  yaw, pitch;
    private final boolean cut;   // true = corte directo (primer frame tras pausa)

    public CinematicFrame(double x, double y, double z,
                          float yaw, float pitch, boolean cut) {
        this.x     = x;
        this.y     = y;
        this.z     = z;
        this.yaw   = yaw;
        this.pitch = pitch;
        this.cut   = cut;
    }

    public double  getX()     { return x; }
    public double  getY()     { return y; }
    public double  getZ()     { return z; }
    public float   getYaw()   { return yaw; }
    public float   getPitch() { return pitch; }
    public boolean isCut()    { return cut; }

    // ── Serialización compacta ────────────────────────────────────────────────

    /**
     * Serializa a string compacto para YAML: "x,y,z,yaw,pitch" o "x,y,z,yaw,pitch,cut"
     * Usa 4 decimales para x/y/z, 2 para yaw/pitch — suficiente precisión.
     */
    public String serialize() {
        String base = String.format("%.4f,%.4f,%.4f,%.2f,%.2f",
                x, y, z, yaw, pitch);
        return cut ? base + ",cut" : base;
    }

    /** Deserializa desde el formato compacto. */
    public static CinematicFrame deserialize(String s) {
        String[] parts = s.split(",");
        if (parts.length < 5) throw new IllegalArgumentException("Frame inválido: " + s);
        double  fx    = Double.parseDouble(parts[0]);
        double  fy    = Double.parseDouble(parts[1]);
        double  fz    = Double.parseDouble(parts[2]);
        float   fyaw  = Float.parseFloat(parts[3]);
        float   fpitch = Float.parseFloat(parts[4]);
        boolean fcut  = parts.length >= 6 && parts[5].equals("cut");
        return new CinematicFrame(fx, fy, fz, fyaw, fpitch, fcut);
    }
}