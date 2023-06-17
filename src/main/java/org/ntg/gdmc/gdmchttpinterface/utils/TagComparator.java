package org.ntg.gdmc.gdmchttpinterface.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class TagComparator {

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

}
