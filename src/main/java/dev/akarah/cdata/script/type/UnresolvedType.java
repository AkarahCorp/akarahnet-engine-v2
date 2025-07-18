package dev.akarah.cdata.script.type;

import dev.akarah.cdata.script.jvm.CodegenUtil;
import dev.akarah.cdata.script.value.RuntimeValue;
import dev.akarah.cdata.script.value.mc.RItem;

import java.lang.constant.ClassDesc;

public record UnresolvedType(String name) implements Type<RuntimeValue> {
    @Override
    public String typeName() {
        return this.name();
    }

    @Override
    public Class<RuntimeValue> typeClass() {
        return RuntimeValue.class;
    }

    @Override
    public ClassDesc classDescType() {
        return CodegenUtil.ofClass(RuntimeValue.class);
    }
}
