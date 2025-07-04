package dev.akarah.cdata.script.expr.vec3;

import com.mojang.datafixers.util.Pair;
import dev.akarah.cdata.script.jvm.CodegenUtil;
import dev.akarah.cdata.script.expr.Expression;
import dev.akarah.cdata.script.jvm.CodegenContext;
import dev.akarah.cdata.script.type.Type;
import net.minecraft.world.phys.Vec3;

import java.lang.constant.MethodTypeDesc;
import java.util.List;

public record Vec3MultiplyExpression(
        Expression lhs,
        Expression rhs
) implements Expression {
    @Override
    public void compile(CodegenContext ctx) {
        ctx
                .pushValue(this.lhs)
                .typecheck(Vec3.class)
                .pushValue(this.rhs)
                .typecheck(Vec3.class)
                .bytecode(cb -> cb.invokevirtual(
                        CodegenUtil.ofClass(Vec3.class),
                        "multiply",
                        MethodTypeDesc.of(
                                CodegenUtil.ofClass(Vec3.class),
                                List.of(CodegenUtil.ofClass(Vec3.class))
                        )
                ));
    }

    @Override
    public Type<?> type(CodegenContext ctx) {
        return Type.vec3();
    }

    public static List<Pair<String, Type<?>>> fields() {
        return List.of(
                Pair.of("lhs", Type.vec3()),
                Pair.of("rhs", Type.vec3())
        );
    }
}
