package nl.nielspoldervaart.gdmc.common.handlers;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea;

import java.io.IOException;

public class BuildAreaHandler extends HandlerBase {

    public BuildAreaHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    public void internalHandle(HttpExchange httpExchange) throws IOException {
        String method = httpExchange.getRequestMethod().toLowerCase();
        if (!method.equals("get")) {
            throw new HttpException("Method not allowed. Use GET method to request the build area.", 405);
        }

        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);

        resolveRequest(httpExchange, BuildArea.toJSONString());
    }
}
