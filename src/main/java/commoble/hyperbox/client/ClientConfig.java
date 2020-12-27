package commoble.hyperbox.client;

import commoble.hyperbox.ConfigHelper;
import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig
{
//	public static final Codec<Map<String,String>> STRING_TO_STRING_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);
//	public static final Map<String,String> DEFAULT_ALIAS_MAP = ImmutableMap.of("example_alias", "example_command");
//	
//	public final ConfigObjectListener<Map<String, String>> aliases;
	

	public ClientConfig(ForgeConfigSpec.Builder builder, ConfigHelper.Subscriber subscriber)
	{
//		builder.comment(
//			"Lookup table of user-defined command aliases.",
//			"Field name is the alias the user would type, the field value is the command that will be used by using that alias");
//		this.aliases = subscriber.subscribeObject(builder, "aliases", STRING_TO_STRING_MAP_CODEC, DEFAULT_ALIAS_MAP);
	}
}