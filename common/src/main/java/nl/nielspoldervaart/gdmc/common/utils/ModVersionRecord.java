package nl.nielspoldervaart.gdmc.common.utils;

public record ModVersionRecord() {
	public static String MOD_VERSION;

	public static void setModVersion(String version) {
		MOD_VERSION = version;
	}

	public static String getModVersion() {
		return MOD_VERSION == null ? ModVersionRecord.class.getPackage().getImplementationVersion() : MOD_VERSION;
	}
}
