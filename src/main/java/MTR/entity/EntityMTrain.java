package mtr.entity;

import net.minecraft.world.World;

public class EntityMTrain extends EntityTrain {

	public EntityMTrain(World worldIn) {
		super(worldIn);
	}

	public EntityMTrain(World worldIn, double x, double y, double z, int trainType) {
		super(worldIn, x, y, z, trainType);
	}

	@Override
	public int getSiblingSpacing() {
		return 16;
	}

	@Override
	public float getEndSpacing() {
		return 4.5F;
	}
}