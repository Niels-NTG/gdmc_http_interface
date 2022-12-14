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

	public void visitString(StringTag p_178228_) {
		this.builder.append(StringTag.quoteAndEscape(p_178228_.getAsString()));
	}

	public void visitByte(ByteTag p_178217_) {
		this.builder.append(p_178217_.getAsNumber());
	}

	public void visitShort(ShortTag p_178227_) {
		this.builder.append(p_178227_.getAsNumber());
	}

	public void visitInt(IntTag p_178223_) {
		this.builder.append(p_178223_.getAsNumber());
	}

	public void visitLong(LongTag p_178226_) {
		this.builder.append(p_178226_.getAsNumber());
	}

	public void visitFloat(FloatTag p_178221_) {
		this.builder.append(p_178221_.getAsFloat());
	}

	public void visitDouble(DoubleTag p_178219_) {
		this.builder.append(p_178219_.getAsDouble());
	}

	public void visitByteArray(ByteArrayTag p_178216_) {
		this.builder.append('[');
		byte[] byteArray = p_178216_.getAsByteArray();
		for(int i = 0; i < byteArray.length; ++i) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append(byteArray[i]);
		}
		this.builder.append(']');
	}

	public void visitIntArray(IntArrayTag p_178222_) {
		this.builder.append('[');
		int[] intArray = p_178222_.getAsIntArray();
		for(int i = 0; i < intArray.length; ++i) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append(intArray[i]);
		}
		this.builder.append(']');
	}

	public void visitLongArray(LongArrayTag p_178225_) {
		this.builder.append('[');
		long[] longArray = p_178225_.getAsLongArray();
		for (int i = 0; i < longArray.length; i++) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append(longArray[i]);
		}
		this.builder.append(']');
	}

	public void visitList(ListTag p_178224_) {
		this.builder.append('[');
		for(int i = 0; i < p_178224_.size(); ++i) {
			if (i != 0) {
				this.builder.append(',');
			}

			this.builder.append((new JsonTagVisitor()).visit(p_178224_.get(i)));
		}
		this.builder.append(']');
	}

	public void visitCompound(CompoundTag p_178218_) {
		this.builder.append('{');
		List<String> list = Lists.newArrayList(p_178218_.getAllKeys());
		Collections.sort(list);

		for (String s : list) {
			if (this.builder.length() != 1) {
				this.builder.append(',');
			}
			this.builder.append(handleEscape(s)).append(':').append((new JsonTagVisitor()).visit(p_178218_.get(s)));
		}
		this.builder.append('}');
	}

	private static String handleEscape(String s) {
		return SIMPLE_VALUE.matcher(s).matches() ? s : StringTag.quoteAndEscape(s);
	}

	public void visitEnd(EndTag p_178220_) {

	}
}
