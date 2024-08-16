package nl.nielspoldervaart.gdmc.fabric.config;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import nl.nielspoldervaart.gdmc.fabric.GdmcHttpMod;

public class GdmcHttpConfig {

	public static final int DEFAULT_HTTP_INTERFACE_PORT = 9000;

	public static int HTTP_INTERFACE_PORT = DEFAULT_HTTP_INTERFACE_PORT;

	public static void loadConfig(String filePath) {
		try {
			JsonElement json = JsonParser.parseReader(new FileReader(filePath));
			if (json.isJsonObject()) {
				JsonObject object = (JsonObject) json;
				HTTP_INTERFACE_PORT = object.get("http_port").getAsInt();
			}
		} catch (JsonIOException | JsonSyntaxException | FileNotFoundException ignored) {}
	}

	public static void saveConfig(String filePath) {
		JsonObject json = new JsonObject();
		json.addProperty("http_port", HTTP_INTERFACE_PORT);
		try {
			BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(filePath));
			bufferedWriter.write(json.toString());
			bufferedWriter.close();
		} catch (IOException e) {
			GdmcHttpMod.LOGGER.error("Failed to save config file", e.getMessage());
		}
	}

}
