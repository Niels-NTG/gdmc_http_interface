package nl.nielspoldervaart.gdmc.common.handlers;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea;

import java.io.IOException;
import java.util.Map;

public class BuildAreaHandler extends HandlerBase {

	public BuildAreaHandler(MinecraftServer mcServer) {
		super(mcServer);
	}

	@Override
	protected void internalHandle(HttpExchange httpExchange) throws IOException {

		if (!httpExchange.getRequestMethod().equalsIgnoreCase("get")) {
			throw new HttpException("Method not allowed. Use GET method to request the build area.", 405);
		}

		Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getQuery());

		String buildAreaName = queryParams.get("name");

		String buildAreaJSON;
		if (buildAreaName == null) {
			buildAreaJSON = BuildArea.getCurrentBuildArea().toJSONString();
		} else {
			BuildArea.BuildAreaInstance buildArea = BuildArea.createBuildAreaInstanceFromData(mcServer, buildAreaName);
			if (buildArea == null) {
				throw new HttpException("Build area '" + buildAreaName + "' not found.", 404);
			}
			buildAreaJSON = buildArea.toJSONString();
		}

		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);

		resolveRequest(httpExchange, buildAreaJSON);

	}
}
