package nl.nielspoldervaart.gdmc.neoforge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GdmcHttpConfig {

	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.ConfigValue<Integer> HTTP_INTERFACE_PORT;

	static {
		BUILDER.push("Config for GDMC HTTP Interface");

		HTTP_INTERFACE_PORT = BUILDER.comment("Port number for HTTP interface").defineInRange("gdmc http port", 9000, 0, 65535);

		BUILDER.pop();

		SPEC = BUILDER.build();
	}
}
