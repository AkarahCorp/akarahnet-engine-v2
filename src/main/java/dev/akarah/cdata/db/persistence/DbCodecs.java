package dev.akarah.cdata.db.persistence;

import dev.akarah.cdata.script.value.*;
import dev.akarah.cdata.script.value.mc.RVector;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbCodecs {
    public static StreamCodec<RegistryFriendlyByteBuf, RuntimeValue> DYNAMIC_CODEC = StreamCodec.recursive(selfCodec -> StreamCodec.of(
            (buf, object) -> {
                switch (object) {
                    case RNumber d -> {
                        buf.writeVarInt(1);
                        buf.writeDouble(d.doubleValue());
                    }
                    case RString s -> {
                        buf.writeVarInt(2);
                        buf.writeByteArray(s.javaValue().getBytes(StandardCharsets.UTF_8));
                    }
                    case RList arrayList -> {
                        buf.writeVarInt(3);
                        buf.writeVarInt(arrayList.javaValue().size());
                        for(var element : arrayList.javaValue()) {
                            selfCodec.encode(buf, element);
                        }
                    }
                    case RDict map -> {
                        buf.writeVarInt(4);
                        buf.writeVarInt(map.javaValue().size());
                        for(var entry : map.javaValue().entrySet()) {
                            selfCodec.encode(buf, entry.getKey());
                            selfCodec.encode(buf, entry.getValue());
                        }
                    }
                    case RStruct struct -> {
                        buf.writeVarInt(5);
                        buf.writeVarInt(struct.javaValue().length);
                        for(var entry : struct.javaValue()) {
                            selfCodec.encode(buf, entry);
                        }
                    }
                    case RVector vector -> {
                        buf.writeVarInt(6);
                        Vec3.STREAM_CODEC.encode(buf, vector.javaValue());
                    }
                    case RUuid uuid -> {
                        buf.writeVarInt(7);
                        UUIDUtil.STREAM_CODEC.encode(buf, uuid.javaValue());
                    }
                    default -> throw new RuntimeException("Unable to make a codec out of " + object);
                }
            },
            (buf) -> {
                var id = buf.readVarInt();
                switch (id) {
                    case 1 -> {
                        return RNumber.of(buf.readDouble());
                    }
                    case 2 -> {
                        return RString.of(new String(buf.readByteArray(), StandardCharsets.UTF_8));
                    }
                    case 3 -> {
                        var list = RList.create();
                        var size = buf.readVarInt();
                        for(int i = 0; i < size; i++) {
                            RList.add(list, selfCodec.decode(buf));
                        }
                        return list;
                    }
                    case 4 -> {
                        var map = RDict.create();
                        var size = buf.readVarInt();
                        for(int i = 0; i < size; i++) {
                            RDict.put(map, selfCodec.decode(buf), selfCodec.decode(buf));
                        }
                        return map;
                    }
                    case 5 -> {
                        var size = buf.readVarInt();
                        var struct = RStruct.create(size);
                        for(int i = 0; i < size; i++) {
                            RStruct.put(struct, i, selfCodec.decode(buf));
                        }
                        return struct;
                    }
                    case 6 -> {
                        return RVector.of(Vec3.STREAM_CODEC.decode(buf));
                    }
                    case 7 -> {
                        return RUuid.of(UUIDUtil.STREAM_CODEC.decode(buf));
                    }
                    default -> throw new RuntimeException("Unknown id " + id);
                }
            }
    ));

    public static StreamCodec<RegistryFriendlyByteBuf, Object2ObjectAVLTreeMap<String, RuntimeValue>> TREE_MAP_CODEC = StreamCodec.of(
            (buf, map) -> {
                buf.writeVarInt(map.size());
                for(var entry : map.entrySet()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                    DYNAMIC_CODEC.encode(buf, entry.getValue());
                }
            },
            (buf) -> {
                var map = new Object2ObjectAVLTreeMap<String, RuntimeValue>();
                var entries = buf.readVarInt();
                for(int i = 0; i < entries; i++) {
                    var key = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var value = DYNAMIC_CODEC.decode(buf);
                    map.put(key, value);
                }
                return map;
            }
    );


}
