package dev.akarah.cdata.script.expr.entity;

import com.mojang.datafixers.util.Pair;
import dev.akarah.cdata.script.jvm.CodegenUtil;
import dev.akarah.cdata.script.expr.Expression;
import dev.akarah.cdata.script.jvm.CodegenContext;
import dev.akarah.cdata.script.type.Type;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.lang.constant.MethodTypeDesc;
import java.util.List;

public record EntityTeleportRelativeAction(
        Expression entityExpression,
        Expression position
) implements Expression {
    @Override
    public void compile(CodegenContext ctx) {
        ctx
                .pushValue(this.entityExpression)
                .typecheck(Entity.class)
                .pushValue(this.position)
                .typecheck(Vec3.class)
                .bytecode(cb -> cb.astore(1))
                .bytecode(cb -> cb.aload(1))
                .getVectorComponent("x")
                .bytecode(cb -> cb.aload(1))
                .getVectorComponent("y")
                .bytecode(cb -> cb.aload(1))
                .getVectorComponent("z")
                .bytecode(cb -> cb.invokevirtual(
                        CodegenUtil.ofClass(Entity.class),
                        "teleportRelative",
                        MethodTypeDesc.of(
                                CodegenUtil.ofVoid(),
                                List.of(CodegenUtil.ofDouble(), CodegenUtil.ofDouble(), CodegenUtil.ofDouble())
                        )
                ));
    }

    @Override
    public Type<?> type(CodegenContext ctx) {
        return Type.void_();
    }

    public static List<Pair<String, Type<?>>> fields() {
        return List.of(
                Pair.of("entity", Type.entity()),
                Pair.of("position", Type.vec3())
        );
    }
}
