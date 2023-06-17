package org.ntg.gdmc.gdmchttpinterface.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class TagMerger {

	/**
	 * Merge patch with target compound where the key and the tag type is the same, or if the key
	 * does not exist at the source.
	 *
	 * @param targetCompound existing compound tag to merge into
	 * @param patchCompound existing compoundtag to merge from
	 * @return targetComponent
	 */
	public static CompoundTag merge(CompoundTag targetCompound, CompoundTag patchCompound) {
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
				TagMerger.merge(
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

}
