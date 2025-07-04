package dev.akarah.cdata.script.dsl;

import com.mojang.datafixers.util.Pair;
import dev.akarah.cdata.registry.text.Parser;
import dev.akarah.cdata.script.exception.ParsingException;
import dev.akarah.cdata.script.exception.SpanData;
import dev.akarah.cdata.script.expr.Expression;
import dev.akarah.cdata.script.expr.SpannedExpression;
import dev.akarah.cdata.script.expr.bool.BooleanExpression;
import dev.akarah.cdata.script.expr.flow.*;
import dev.akarah.cdata.script.expr.number.*;
import dev.akarah.cdata.script.expr.string.StringExpression;
import dev.akarah.cdata.script.expr.text.TextExpression;
import dev.akarah.cdata.script.type.SpannedType;
import dev.akarah.cdata.script.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DslParser {
    List<DslToken> tokens;
    int index;

    public static SchemaExpression parseTopLevelExpression(List<DslToken> tokens) {
        var parser = new DslParser();
        parser.tokens = tokens;

        return parser.parseSchema();
    }

    public SchemaExpression parseSchema() {
        expect(DslToken.SchemaKeyword.class);
        expect(DslToken.OpenParen.class);

        var parameters = new ArrayList<Pair<String, Type<?>>>();
        while(!(peek() instanceof DslToken.CloseParen)) {
            var name = expect(DslToken.Identifier.class);
            var type = parseType();
            parameters.add(Pair.of(name.identifier(), type));
            if(!(peek() instanceof DslToken.CloseParen)) {
                expect(DslToken.Comma.class);
            }
        }
        expect(DslToken.CloseParen.class);

        Type<?> returnType = Type.void_();
        if(peek() instanceof DslToken.ArrowSymbol) {
            expect(DslToken.ArrowSymbol.class);
            returnType = parseType();
        }

        var body = parseBlock();
        return new SchemaExpression(parameters, returnType, body);
    }

    public Type<?> parseType() {
        var identifier = expect(DslToken.Identifier.class);
        return switch (identifier.identifier()) {
            case "any" -> new SpannedType<>(Type.any(), identifier.span());
            case "void" -> new SpannedType<>(Type.void_(), identifier.span());
            case "number" -> new SpannedType<>(Type.number(), identifier.span());
            case "bool" -> new SpannedType<>(Type.bool(), identifier.span());
            case "string" -> new SpannedType<>(Type.string(), identifier.span());
            case "vec3" -> new SpannedType<>(Type.vec3(), identifier.span());
            case "text" -> new SpannedType<>(Type.text(), identifier.span());
            case "list" -> new SpannedType<>(Type.list(), identifier.span());
            case "entity" -> new SpannedType<>(Type.entity(), identifier.span());
            default -> throw new ParsingException("Type `" + identifier + "` is unknown.", identifier.span());
        };
    }

    public Expression parseStatement() {
        if(this.peek() instanceof DslToken.RepeatKeyword) {
            return parseRepeat();
        }
        if(this.peek() instanceof DslToken.IfKeyword) {
            return parseIf();
        }
        if(this.peek() instanceof DslToken.ForeachKeyword) {
            return parseForEach();
        }
        return parseValue();
    }

    public Expression parseValue() {
        return this.parseStorage();
    }

    public ForEachAction parseForEach() {
        expect(DslToken.ForeachKeyword.class);
        var variableName = expect(DslToken.Identifier.class);
        expect(DslToken.InKeyword.class);
        var listExpr = parseBaseExpression();
        var block = parseBlock();
        return new ForEachAction(listExpr, variableName.identifier(), block);
    }

    public RepeatTimesAction parseRepeat() {
        expect(DslToken.RepeatKeyword.class);
        var times = parseValue();
        var block = parseBlock();

        return new RepeatTimesAction(times, block);
    }

    public IfAction parseIf() {
        expect(DslToken.IfKeyword.class);
        var times = parseValue();
        var block = parseBlock();

        var orElse = Optional.<Expression>empty();
        if(peek() instanceof DslToken.ElseKeyword) {
            expect(DslToken.ElseKeyword.class);
            orElse = Optional.of(parseBlock());
        }

        return new IfAction(times, block, orElse);
    }

    public AllOfAction parseBlock() {
        var statements = new ArrayList<Expression>();
        expect(DslToken.OpenBrace.class);
        while(!(peek() instanceof DslToken.CloseBrace)) {
            statements.add(parseStatement());
        }
        expect(DslToken.CloseBrace.class);
        return new AllOfAction(statements);
    }

    public Expression parseStorage() {
        var baseExpression = parseArrowExpression();
        while(peek() instanceof DslToken.EqualSymbol
        && baseExpression instanceof GetLocalAction(String variable)) {
            expect(DslToken.EqualSymbol.class);
            baseExpression = new SpannedExpression<>(new SetLocalAction(variable, parseValue()), baseExpression.span());
        }
        return baseExpression;
    }

    public Expression parseArrowExpression() {
        var baseExpression = parseTerm();
        while(peek() instanceof DslToken.ArrowSymbol) {
            expect(DslToken.ArrowSymbol.class);
            var name = expect(DslToken.Identifier.class);
            var parameters = parseTuple();
            parameters.addFirst(baseExpression);
            baseExpression = new SpannedExpression<>(new LateResolvedFunctionCall(name.identifier(), parameters), name.span());
        }
        return baseExpression;
    }

    public Expression parseTerm() {
        var base = parseFactor();
        while(peek() instanceof DslToken.PlusSymbol) {
            expect(DslToken.PlusSymbol.class);
            base = new AddExpression(base, parseFactor());
        }
        while(peek() instanceof DslToken.StarSymbol) {
            expect(DslToken.StarSymbol.class);
            base = new MultiplyExpression(base, parseFactor());
        }
        return base;
    }

    public Expression parseFactor() {
        var base = parseInvocation();
        while(peek() instanceof DslToken.MinusSymbol) {
            expect(DslToken.MinusSymbol.class);
            base = new SubtractExpression(base, parseInvocation());
        }
        while(peek() instanceof DslToken.SlashSymbol) {
            expect(DslToken.SlashSymbol.class);
            base = new DivideExpression(base, parseInvocation());
        }
        return base;
    }

    public Expression parseInvocation() {
        var baseExpression = parseBaseExpression();

        if(peek() instanceof DslToken.OpenParen && baseExpression instanceof GetLocalAction(String functionName)) {
            var tuple = parseTuple();
            baseExpression = new SpannedExpression<>(new LateResolvedFunctionCall(functionName, tuple), baseExpression.span());
        }
        return baseExpression;
    }

    public ArrayList<Expression> parseTuple() {
        expect(DslToken.OpenParen.class);
        var parameters = new ArrayList<Expression>();
        while(!(peek() instanceof DslToken.CloseParen)) {
            parameters.add(parseValue());

            if(!(peek() instanceof DslToken.CloseParen)) {
                expect(DslToken.Comma.class);
            }
        }
        expect(DslToken.CloseParen.class);
        return parameters;
    }

    public Expression parseBaseExpression() {
        var tok = read();
        return switch (tok) {
            case DslToken.NumberExpr numberExpr -> new NumberExpression(numberExpr.value());
            case DslToken.StringExpr stringExpr -> new StringExpression(stringExpr.value());
            case DslToken.TextExpr textExpr -> new TextExpression(Parser.parseTextLine(textExpr.value()));
            case DslToken.Identifier(String id, SpanData span) when id.equals("true") -> new BooleanExpression(true);
            case DslToken.Identifier(String id, SpanData span) when id.equals("false") -> new BooleanExpression(false);
            case DslToken.Identifier identifier -> new GetLocalAction(identifier.identifier());
            default -> throw new ParsingException(tok + " is not a valid value, expected one of: Number, String, Text, Identifier", tok.span());
        };
    }

    public DslToken peek() {
        return this.tokens.get(this.index);
    }

    public DslToken read() {
        return this.tokens.get(this.index++);
    }

    public <T extends DslToken> T expect(Class<T> clazz) {
        var token = read();
        if(clazz.isInstance(token)) {
            return clazz.cast(token);
        } else {
            throw new ParsingException("Expected " + clazz.getSimpleName() + ", but instead found " + token.getClass().getSimpleName(), token.span());
        }
    }
}
