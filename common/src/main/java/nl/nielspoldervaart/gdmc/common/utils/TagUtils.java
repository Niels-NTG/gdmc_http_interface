package nl.nielspoldervaart.gdmc.common.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
#if (MC_VER != MC_1_21_4)
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

		for (String newCompoundKey : newCompound.getAllKeys()) {
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
		for (String patchKey : patchCompound.getAllKeys()) {
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
					targetCompound.getCompound(patchKey),
					patchCompound.getCompound(patchKey)
				);
				continue;
			}

			if (patchTag.getId() == Tag.TAG_LIST && targetTag.getId() == Tag.TAG_LIST) {
				ListTag patchListTag = (ListTag) patchTag;
				ListTag targetListTag = (ListTag) targetTag;
				if (patchListTag.getElementType() == targetListTag.getElementType()) {
					targetCompound.put(patchKey, patchTag.copy());
				}
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
		CompoundTag tag = new CompoundTag();
		String entityId = getEntityId(entity);
		if (entityId != null) {
			tag.putString("id", entityId);
		}
		return entity.saveWithoutId(tag);
	}

	public static byte[] NBTToBytes(CompoundTag tag, boolean returnCompressed) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (returnCompressed) {
			#if (MC_VER == MC_1_21_4)
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
}
