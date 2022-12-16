package com.gdmc.httpinterfacemod.utils;

import net.minecraft.nbt.*;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class JsonTagVisitor implements TagVisitor {
	private final StringBuilder builder = new StringBuilder();
	private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");

	public String visit(Tag tag) {
		tag.accept(this);
		return this.builder.toString();
	}

	public void visitString(StringTag stringTag) {
		this.builder.append(StringTag.quoteAndEscape(stringTag.getAsString()));
	}

	public void visitByte(ByteTag byteTag) {
		this.builder.append(byteTag.getAsNumber());
	}

	public void visitShort(ShortTag shortTag) {
		this.builder.append(shortTag.getAsNumber());
	}

	public void visitInt(IntTag intTag) {
		this.builder.append(intTag.getAsNumber());
	}

	public void visitLong(LongTag longTag) {
		this.builder.append(longTag.getAsNumber());
	}

	public void visitFloat(FloatTag floatTag) {
		this.builder.append(floatTag.getAsFloat());
	}

	public void visitDouble(DoubleTag doubleTag) {
		this.builder.append(doubleTag.getAsDouble());
	}

	public void visitByteArray(ByteArrayTag byteTags) {
		this.builder.append('[');
		byte[] byteArray = byteTags.getAsByteArray();
		for(int i = 0; i < byteArray.length; i++) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append(byteArray[i]);
		}
		this.builder.append(']');
	}

	public void visitIntArray(IntArrayTag intTags) {
		this.builder.append('[');
		int[] intArray = intTags.getAsIntArray();
		for(int i = 0; i < intArray.length; i++) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append(intArray[i]);
		}
		this.builder.append(']');
	}

	public void visitLongArray(LongArrayTag longTags) {
		this.builder.append('[');
		long[] longArray = longTags.getAsLongArray();
		for (int i = 0; i < longArray.length; i++) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append(longArray[i]);
		}
		this.builder.append(']');
	}

	public void visitList(ListTag listTag) {
		this.builder.append('[');
		for(int i = 0; i < listTag.size(); i++) {
			if (i != 0) {
				this.builder.append(',');
			}

			this.builder.append((new JsonTagVisitor()).visit(listTag.get(i)));
		}
		this.builder.append(']');
	}

	public void visitCompound(CompoundTag compoundTag) {
		this.builder.append('{');
		List<String> list = Lists.newArrayList(compoundTag.getAllKeys());
		Collections.sort(list);

		for (String s : list) {
			if (this.builder.length() != 1) {
				this.builder.append(',');
			}
			this.builder.append(handleEscape(s)).append(':').append((new JsonTagVisitor()).visit(compoundTag.get(s)));
		}
		this.builder.append('}');
	}

	private static String handleEscape(String s) {
		return SIMPLE_VALUE.matcher(s).matches() ? s : StringTag.quoteAndEscape(s);
	}

	public void visitEnd(EndTag endTag) {}
}
