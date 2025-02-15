package nl.nielspoldervaart.gdmc.common.utils;

import com.google.gson.*;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.LowerCaseEnumTypeAdapterFactory;

import java.lang.reflect.Type;

public class ChatComponentDataExtractor {

	private static final ExclusionStrategy exclusionStrategy = new ExclusionStrategy() {
		@Override
		public boolean shouldSkipField(FieldAttributes f) {
			return f.getName().equals("decomposedWith") ||
				f.getName().equals("decomposedParts") ||
				f.getName().equals("siblings") ||
				f.getName().equals("style") ||
				f.getName().equals("visualOrderText");
		}

		@Override
		public boolean shouldSkipClass(Class<?> clazz) {
			return false;
		}
	};

	private static final JsonSerializer<MutableComponent> serializer = new JsonSerializer<>() {
		@Override
		public JsonElement serialize(MutableComponent component, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject jsonObject = new JsonObject();

			extractValues(jsonObject, component);

			return jsonObject;
		}

		private static void extractValues(JsonObject targetJson, MutableComponent component) {
			ComponentContents componentContents = component.getContents();
			if (componentContents instanceof TranslatableContents translatableContents) {
				if (translatableContents.getArgs().length > 0) {
					JsonArray jsonArgsArray = new JsonArray();
					for (Object object : translatableContents.getArgs()) {
						if (object instanceof MutableComponent) {
							extractValues(targetJson, (MutableComponent) object);
							continue;
						}
						if (object instanceof Number) {
							jsonArgsArray.add((Number)object);
						} else if (object instanceof Boolean) {
							jsonArgsArray.add((Boolean)object);
						} else {
							jsonArgsArray.add(String.valueOf(object));
						}
					}
					if (!jsonArgsArray.isEmpty()) {
						targetJson.add(translatableContents.getKey(), jsonArgsArray);
					}
				}
			}
		}
	};

	private static final Gson CUSTOM_GSON = Util.make(() -> {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.disableHtmlEscaping();
		gsonBuilder.addSerializationExclusionStrategy(exclusionStrategy);
		gsonBuilder.registerTypeHierarchyAdapter(MutableComponent.class, serializer);
		gsonBuilder.registerTypeAdapterFactory(new LowerCaseEnumTypeAdapterFactory());
		return gsonBuilder.create();
	});

	public static JsonElement toJsonTree(Component component) {
		return CUSTOM_GSON.toJsonTree(component.copy());
	}
}
