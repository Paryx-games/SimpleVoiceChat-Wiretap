package de.maxhenkel.wiretap.wiretap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public class DimensionLocation {

    private final ServerLevel level;
    private final BlockPos pos;

    public DimensionLocation(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos = pos;
    }

    public ResourceLocation getDimension() {
        return level.dimension().location();
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getX() {
        return pos.getX();
    }

    public int getY() {
        return pos.getY();
    }

    public int getZ() {
        return pos.getZ();
    }

    public boolean isDimension(Level level) {
        return level.dimension().location().equals(getDimension());
    }

    public double getDistance(Vec3 p) {
        return Math.sqrt(p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DimensionLocation that = (DimensionLocation) o;

        if (!Objects.equals(getDimension(), that.getDimension())) return false;
        return Objects.equals(pos, that.pos);
    }

    @Override
    public int hashCode() {
        int result = getDimension() != null ? getDimension().hashCode() : 0;
        result = 31 * result + (pos != null ? pos.hashCode() : 0);
        return result;
    }

}
