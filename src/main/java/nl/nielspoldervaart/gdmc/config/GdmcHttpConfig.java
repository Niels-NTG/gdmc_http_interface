package nl.nielspoldervaart.gdmc.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class GdmcHttpConfig {

	public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
	public static final ForgeConfigSpec SPEC;

	public static final ForgeConfigSpec.ConfigValue<Integer> HTTP_INTERFACE_PORT;

	static {
		BUILDER.push("Config for GDMC HTTP Interface");

		HTTP_INTERFACE_PORT = BUILDER.comment("Port number for HTTP interface").defineInRange("gdmc http port", 9000, 0, 65535);

		BUILDER.pop();

		SPEC = BUILDER.build();
	}

}
