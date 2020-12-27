package commoble.hyperbox.client;

import net.minecraft.client.world.DimensionRenderInfo;
import net.minecraft.util.math.vector.Vector3d;

public class HyperboxRenderInfo extends DimensionRenderInfo
{

	public HyperboxRenderInfo()
	{	// args are:
		// cloud height -- NaN for nether and end, only overworld has clouds
		// has ground -- affects sky color (true for overworld and nether, false for end)
		// fog type -- overworld has NORMAL, nether has NONE, end has END
		// force bright lightmap -- affects *color* of lighting -- only true for end
		// constant ambient light -- makes bottom of blocks appear brighter, tops appear dimmer -- only true for nether
        super(Float.NaN, true, DimensionRenderInfo.FogType.NONE, true, true);
	}

	// get brightness dependent fog color
	// overworld does some math, nether just returns the input, end scales it toward black
	@Override
	public Vector3d func_230494_a_(Vector3d colorIn, float brightness)
	{
		return colorIn;
	}

	// is foggy -- only nether returns true
	@Override
	public boolean func_230493_a_(int x, int z)
	{
		return true;
	}

}
