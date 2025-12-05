package nl.nielspoldervaart.gdmc.common.utils;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
#if (MC_VER == MC_1_21_10)
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
#endif

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
#if (MC_VER != MC_1_21_4 && MC_VER != MC_1_21_10)
import java.util.zip.GZIPOutputStream;
#endif

public class TagUtils {

	public static boolean contains(CompoundTag existingCompound, CompoundTag newCompound) {
		if (existingCompound == newCompound) {
			return true;
		}

		if (existingCompound.isEmpty() != newCompound.isEmpty()) {
			return false;
		}

		for (String newCompoundKey : TagUtils.tagKeys(newCompound)) {
			Tag existingTag = existingCompound.get(newCompoundKey);
			if (existingTag == null) {
				return false;
			}
			if (!existingTag.equals(newCompound.get(newCompoundKey))) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Merge patch with target compound where the key and the tag type is the same, or if the key
	 * does not exist at the source.
	 *
	 * @param targetCompound existing compound tag to merge into
	 * @param patchCompound existing compound tag to merge from
	 * @return targetComponent
	 */
	public static CompoundTag mergeTags(CompoundTag targetCompound, CompoundTag patchCompound) {
		for (String patchKey : TagUtils.tagKeys(patchCompound)) {
			Tag patchTag = patchCompound.get(patchKey);
			if (patchTag == null) {
				continue;
			}

			if (!targetCompound.contains(patchKey)) {
				targetCompound.put(patchKey, patchTag.copy());
				continue;
			}

			Tag targetTag = targetCompound.get(patchKey);
			if (targetTag == null) {
				continue;
			}

			if (patchTag.getId() == Tag.TAG_COMPOUND && targetTag.getId() == Tag.TAG_COMPOUND) {
				TagUtils.mergeTags(
					#if (MC_VER == MC_1_21_10)
					targetCompound.getCompoundOrEmpty(patchKey),
					patchCompound.getCompoundOrEmpty(patchKey)
					#else
					targetCompound.getCompound(patchKey),
					patchCompound.getCompound(patchKey)
					#endif
				);
				continue;
			}

			if (patchTag.getId() == Tag.TAG_LIST && targetTag.getId() == Tag.TAG_LIST) {
				ListTag patchListTag = (ListTag) patchTag;
				ListTag targetListTag = (ListTag) targetTag;
				#if (MC_VER == MC_1_21_10)
				if (patchListTag.getType() == targetListTag.getType()) {
					targetCompound.put(patchKey, patchTag.copy());
				}
				#else
				if (patchListTag.getElementType() == targetListTag.getElementType()) {
					targetCompound.put(patchKey, patchTag.copy());
				}
				#endif
				continue;
			}

			if (patchTag.getId() == targetTag.getId()) {
				targetCompound.put(patchKey, patchTag.copy());
			}
		}

		return targetCompound;
	}

	public static String getEntityId(Entity entity) {
		EntityType<?> entityType = entity.getType();
		ResourceLocation resourceLocation = EntityType.getKey(entityType);
		return entityType.canSerialize() ? resourceLocation.toString() : null;
	}

	public static CompoundTag serializeEntityNBT(Entity entity) {
		#if (MC_VER == MC_1_21_10)
		TagValueOutput tagValueOutput = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		String entityId = getEntityId(entity);
		if (entityId != null) {
			tagValueOutput.putString("id", entityId);
		}
		entity.saveWithoutId(tagValueOutput);
		return tagValueOutput.buildResult();
		#else
		CompoundTag tag = new CompoundTag();
		String entityId = getEntityId(entity);
		if (entityId != null) {
			tag.putString("id", entityId);
		}
		return entity.saveWithoutId(tag);
		#endif
	}

	public static String tagAsString(CompoundTag tag) {
		#if (MC_VER == MC_1_21_10)
		return tag.toString();
		#else
		return tag.getAsString();
		#endif
	}

	public static CompoundTag parseTag(String tagString) throws CommandSyntaxException {
		if (tagString.isBlank()) {
			tagString = "{}";
		}
		#if (MC_VER == MC_1_21_10)
		return TagParser.parseCompoundFully(tagString);
		#else
		return TagParser.parseTag(tagString);
		#endif
	}

	public static byte[] NBTToBytes(CompoundTag tag, boolean returnCompressed) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (returnCompressed) {
			#if (MC_VER == MC_1_21_4 || MC_VER == MC_1_21_10)
			NbtIo.writeCompressed(tag, baos);
            #else
            GZIPOutputStream dos = new GZIPOutputStream(baos);
			NbtIo.writeCompressed(tag, dos);
			dos.flush();
            #endif
			return baos.toByteArray();
		}
		DataOutputStream dos = new DataOutputStream(baos);
		NbtIo.write(tag, dos);
		dos.flush();
		return baos.toByteArray();
	}

	public enum StructureEncoding {
		NBT_UNCOMPRESSED,
		NBT_COMPRESSED,
		SNBT
	}

	private static Set<String> tagKeys(CompoundTag tag) {
		#if (MC_VER == MC_1_21_10)
		return tag.keySet();
		#else
		return tag.getAllKeys();
		#endif
	}
}
