package de.streuland.neighborhood;

/**
 * Shared economic resources for a neighborhood cluster.
 */
public class SharedResourcePool {
    private int water;
    private int xp;
    private int materials;

    public SharedResourcePool() {
        this(0, 0, 0);
    }

    public SharedResourcePool(int water, int xp, int materials) {
        this.water = Math.max(0, water);
        this.xp = Math.max(0, xp);
        this.materials = Math.max(0, materials);
    }

    public int getWater() {
        return water;
    }

    public int getXp() {
        return xp;
    }

    public int getMaterials() {
        return materials;
    }

    public void setWater(int water) {
        this.water = Math.max(0, water);
    }

    public void setXp(int xp) {
        this.xp = Math.max(0, xp);
    }

    public void setMaterials(int materials) {
        this.materials = Math.max(0, materials);
    }

    public void add(SharedResourcePool other) {
        if (other == null) {
            return;
        }
        this.water += other.water;
        this.xp += other.xp;
        this.materials += other.materials;
    }

    public SharedResourcePool copy() {
        return new SharedResourcePool(water, xp, materials);
    }

    @Override
    public String toString() {
        return "Wasser=" + water + ", XP=" + xp + ", Materialien=" + materials;
    }
}
