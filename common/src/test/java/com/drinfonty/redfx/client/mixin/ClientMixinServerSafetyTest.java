package com.drinfonty.redfx.client.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Regression & architecture test to prevent server-thread rendering crashes.
 *
 * In singleplayer / LAN environments, Minecraft runs both client and server in the same JVM.
 * Any class outside of 'net.minecraft.client.*' (such as LevelChunk, LivingEntity, Block, Entity)
 * is a shared/common class that is regularly executed on the background 'Server thread'
 * (e.g. when entities tick, explosions detonate, or chunks are modified by the server).
 *
 * Any client mixin injecting into these common classes MUST guard against execution on the
 * server by verifying 'level.isClientSide()' before touching client stores or render states.
 *
 * This test is completely self-contained and operates directly on compiled class bytecode,
 * requiring zero external bytecode libraries across all Minecraft toolchains.
 */
class ClientMixinServerSafetyTest {

	@Test
	void testCommonMixinsGuardAgainstServerThreadExecution() throws Exception {
		ClassLoader cl = getClass().getClassLoader();
		InputStream is = cl.getResourceAsStream("redfx.client.mixins.json");
		if (is == null) {
			throw new IllegalStateException("Could not find redfx.client.mixins.json on test classpath");
		}

		JsonObject json = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
		String pkg = json.get("package").getAsString();
		JsonArray clientMixins = json.getAsJsonArray("client");

		List<String> violations = new ArrayList<>();

		for (JsonElement el : clientMixins) {
			String mixinSimpleName = el.getAsString();
			String fullClassName = pkg + "." + mixinSimpleName;

			try (InputStream classStream = cl.getResourceAsStream(fullClassName.replace('.', '/') + ".class")) {
				if (classStream == null) {
					continue;
				}
				byte[] classBytes = classStream.readAllBytes();
				ClassFileInfo info = parseClassFile(classBytes);

				for (String target : info.mixinTargets) {
					if (!isClientOnlyClass(target)) {
						for (MethodInfo method : info.injectedMethods) {
							if (!method.invokesIsClientSide) {
								violations.add(String.format(
									"Mixin '%s#%s' targets common class '%s' but does NOT call 'isClientSide()'!"
									+ " In singleplayer, this executes on the Server thread (e.g. Wither/TNT block destruction)"
									+ " and will crash Sodium/Vanilla when client render state is accessed.",
									fullClassName, method.name, target
								));
							}
						}
					}
				}
			}
		}

		assertTrue(violations.isEmpty(),
			"Found client mixins targeting common classes without isClientSide() checks:\n"
			+ String.join("\n", violations));
	}

	private static boolean isClientOnlyClass(String className) {
		return className.startsWith("net.minecraft.client.")
			|| className.startsWith("net/minecraft/client/");
	}

	static class MethodInfo {
		String name;
		boolean invokesIsClientSide;
	}

	static class ClassFileInfo {
		List<String> mixinTargets = new ArrayList<>();
		List<MethodInfo> injectedMethods = new ArrayList<>();
	}

	private static ClassFileInfo parseClassFile(byte[] bytes) throws Exception {
		ClassFileInfo info = new ClassFileInfo();
		DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
		int magic = dis.readInt();
		if (magic != 0xCAFEBABE) {
			throw new IllegalArgumentException("Invalid class file magic: " + Integer.toHexString(magic));
		}
		dis.readUnsignedShort(); // minor
		dis.readUnsignedShort(); // major

		int cpCount = dis.readUnsignedShort();
		Object[] cp = new Object[cpCount];
		int[] cpTags = new int[cpCount];

		// First pass: constant pool
		for (int i = 1; i < cpCount; i++) {
			int tag = dis.readUnsignedByte();
			cpTags[i] = tag;
			switch (tag) {
				case 1: // UTF-8
					cp[i] = dis.readUTF();
					break;
				case 3: // Integer
				case 4: // Float
					dis.readInt();
					break;
				case 5: // Long
				case 6: // Double
					dis.readLong();
					i++; // 8-byte entries take two slots
					break;
				case 7: // Class
				case 8: // String
				case 16: // MethodType
				case 19: // Module
				case 20: // Package
					cp[i] = dis.readUnsignedShort();
					break;
				case 9: // Fieldref
				case 10: // Methodref
				case 11: // InterfaceMethodref
				case 12: // NameAndType
				case 17: // Dynamic
				case 18: // InvokeDynamic
					int part1 = dis.readUnsignedShort();
					int part2 = dis.readUnsignedShort();
					cp[i] = new int[]{part1, part2};
					break;
				case 15: // MethodHandle
					dis.readByte(); // reference_kind
					dis.readUnsignedShort(); // reference_index
					break;
				default:
					break;
			}
		}

		java.util.function.Function<Integer, String> getUtf8 = idx -> {
			if (idx != null && idx > 0 && idx < cp.length && cp[idx] instanceof String s) {
				return s;
			}
			return "";
		};

		java.util.function.Function<Integer, String> getMethodName = idx -> {
			if (idx != null && idx > 0 && idx < cp.length && cp[idx] instanceof int[] parts) {
				int natIdx = parts[1];
				if (natIdx > 0 && natIdx < cp.length && cp[natIdx] instanceof int[] nat) {
					return getUtf8.apply(nat[0]);
				}
			}
			return "";
		};

		dis.readUnsignedShort(); // access_flags
		dis.readUnsignedShort(); // this_class
		dis.readUnsignedShort(); // super_class
		int interfacesCount = dis.readUnsignedShort();
		for (int i = 0; i < interfacesCount; i++) {
			dis.readUnsignedShort();
		}

		// Fields
		int fieldsCount = dis.readUnsignedShort();
		for (int i = 0; i < fieldsCount; i++) {
			dis.readUnsignedShort(); // access_flags
			dis.readUnsignedShort(); // name_index
			dis.readUnsignedShort(); // descriptor_index
			int attrCount = dis.readUnsignedShort();
			for (int a = 0; a < attrCount; a++) {
				dis.readUnsignedShort(); // name_index
				int len = dis.readInt();
				dis.skipBytes(len);
			}
		}

		// Methods
		int methodsCount = dis.readUnsignedShort();
		for (int i = 0; i < methodsCount; i++) {
			dis.readUnsignedShort(); // access_flags
			int nameIndex = dis.readUnsignedShort();
			String methodName = getUtf8.apply(nameIndex);
			dis.readUnsignedShort(); // descriptor_index

			boolean isInject = false;
			boolean callsIsClientSide = false;

			int attrCount = dis.readUnsignedShort();
			for (int a = 0; a < attrCount; a++) {
				int attrNameIndex = dis.readUnsignedShort();
				String attrName = getUtf8.apply(attrNameIndex);
				int attrLen = dis.readInt();
				byte[] attrData = new byte[attrLen];
				dis.readFully(attrData);

				if ("RuntimeVisibleAnnotations".equals(attrName) || "RuntimeInvisibleAnnotations".equals(attrName)) {
					String attrStr = new String(attrData, StandardCharsets.ISO_8859_1);
					if (attrStr.contains("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
						isInject = true;
					}
				} else if ("Code".equals(attrName)) {
					DataInputStream codeDis = new DataInputStream(new ByteArrayInputStream(attrData));
					codeDis.readUnsignedShort(); // max_stack
					codeDis.readUnsignedShort(); // max_locals
					int codeLen = codeDis.readInt();
					byte[] code = new byte[codeLen];
					codeDis.readFully(code);

					for (int c = 0; c < codeLen - 2; c++) {
						int opcode = code[c] & 0xFF;
						if (opcode >= 0xB6 && opcode <= 0xB9) { // invoke opcode
							int methodRefIdx = ((code[c + 1] & 0xFF) << 8) | (code[c + 2] & 0xFF);
							String invokedName = getMethodName.apply(methodRefIdx);
							if ("isClientSide".equals(invokedName) || invokedName.contains("isClientSide")) {
								callsIsClientSide = true;
							}
						}
					}
				}
			}

			if (isInject) {
				MethodInfo mi = new MethodInfo();
				mi.name = methodName;
				mi.invokesIsClientSide = callsIsClientSide;
				info.injectedMethods.add(mi);
			}
		}

		// Class Attributes (find @Mixin annotation targets)
		int classAttrCount = dis.readUnsignedShort();
		for (int a = 0; a < classAttrCount; a++) {
			int attrNameIndex = dis.readUnsignedShort();
			String attrName = getUtf8.apply(attrNameIndex);
			int attrLen = dis.readInt();
			byte[] attrData = new byte[attrLen];
			dis.readFully(attrData);

			if ("RuntimeVisibleAnnotations".equals(attrName) || "RuntimeInvisibleAnnotations".equals(attrName)) {
				for (int i = 1; i < cpCount; i++) {
					if (cp[i] instanceof Integer nameIdx) {
						String clsName = getUtf8.apply(nameIdx);
						if (clsName != null && !clsName.isEmpty() && !clsName.startsWith("java/") && !clsName.startsWith("org/spongepowered/")) {
							String norm = clsName.replace('/', '.');
							if (!norm.startsWith("com.drinfonty.redfx.") && !norm.startsWith("net.minecraft.client.")) {
								info.mixinTargets.add(norm);
							}
						}
					}
				}
			}
		}

		return info;
	}
}
