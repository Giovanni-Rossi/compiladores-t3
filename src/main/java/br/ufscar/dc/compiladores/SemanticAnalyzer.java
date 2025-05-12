package br.ufscar.dc.compiladores;

import br.ufscar.dc.compiladores.JanderParser.*;
import br.ufscar.dc.compiladores.SymbolTable.JanderType;
import org.antlr.v4.runtime.Token;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class SemanticAnalyzer extends JanderBaseVisitor<Void> {
    private SymbolTable symbolTable;
    private PrintWriter pw;

    public SemanticAnalyzer(PrintWriter pw) {
        this.symbolTable = new SymbolTable();
        this.pw = pw;
        JanderSemanticoUtils.semanticErrors.clear();
    }

    public boolean hasErrors() {
        return !JanderSemanticoUtils.semanticErrors.isEmpty();
    }

    public void printErrors() {
        for (String error : JanderSemanticoUtils.semanticErrors) {
            pw.println(error);
        }
        pw.println("Fim da compilacao");
    }

    @Override
    public Void visitPrograma(ProgramaContext ctx) {
        symbolTable = new SymbolTable();
        JanderSemanticoUtils.semanticErrors.clear();
        JanderSemanticoUtils.clearCurrentAssignmentVariableStack();

        if (ctx.declaracoes() != null) {
            visitDeclaracoes(ctx.declaracoes());
        }
        if (ctx.corpo() != null) {
            visitCorpo(ctx.corpo());
        }
        return null;
    }

    @Override
    public Void visitDeclaracoes(DeclaracoesContext ctx) {
        if (ctx == null) return null;
        for (Decl_local_globalContext declCtx : ctx.decl_local_global()) {
            visitDecl_local_global(declCtx);
        }
        return null;
    }

    @Override
    public Void visitCorpo(CorpoContext ctx) {
        if (ctx == null) return null;

        if (ctx.declaracao_local() != null) {
            for (Declaracao_localContext declLocalCtx : ctx.declaracao_local()) {
                visitDeclaracao_local(declLocalCtx);
            }
        }
        if (ctx.cmd() != null) {
            for (CmdContext cmdCtx : ctx.cmd()) {
                visitCmd(cmdCtx);
            }
        }
        return null;
    }

    @Override
    public Void visitDecl_local_global(Decl_local_globalContext ctx) {
        if (ctx.declaracao_local() != null) {
            visitDeclaracao_local(ctx.declaracao_local());
        } else if (ctx.declaracao_global() != null) {
            visitDeclaracao_global(ctx.declaracao_global());
        }
        return super.visitDecl_local_global(ctx);
    }

    public Void visitDeclaracao_global(Declaracao_globalContext ctx) {
        JanderSemanticoUtils.addSemanticError(ctx.getStart(), "Declaracoes globais (procedimentos/funcoes) ainda nao sao totalmente suportadas por este analisador semantico.");
        return super.visitDeclaracao_global(ctx);
    }

    @Override
    public Void visitDeclaracao_local(Declaracao_localContext ctx) {
        if (ctx.variavel() != null) {
            visitVariavel(ctx.variavel());
        } else if (ctx.CONSTANTE() != null && ctx.IDENT() != null && ctx.tipo_basico() != null) {
            String constName = ctx.IDENT().getText();
            String typeString = ctx.tipo_basico().getText();
            JanderType constType = JanderType.INVALID;
            switch (typeString.toLowerCase()) {
                case "inteiro":
                    constType = JanderType.INTEGER;
                    break;
                case "real":
                    constType = JanderType.REAL;
                    break;
                case "literal":
                    constType = JanderType.LITERAL;
                    break;
                case "logico":
                    constType = JanderType.LOGICAL;
                    break;
                default:
                    JanderSemanticoUtils.addSemanticError(ctx.tipo_basico().getStart(), "tipo " + typeString + " desconhecido");
                    break;
            }

            if (symbolTable.containsSymbol(constName)) {
                JanderSemanticoUtils.addSemanticError(ctx.IDENT().getSymbol(), "identificador " + constName + " ja declarado anteriormente");
            } else {
                if (constType != JanderType.INVALID) {
                    symbolTable.addSymbol(constName, constType);
                }
            }
        } else if (ctx.TIPO() != null) {
            JanderSemanticoUtils.addSemanticError(ctx.TIPO().getSymbol(), "Declaracoes de tipo customizadas ('tipo') ainda nao sao totalmente implementadas.");
        }
        return super.visitDeclaracao_local(ctx);
    }

    @Override
    public Void visitVariavel(VariavelContext ctx) {
        String strTipoVar = ctx.tipo().getText();
        JanderType varJanderType = getJanderTypeFromGrammarTipo(ctx.tipo());

        for (IdentificadorContext identCtx : ctx.identificador()) {
            String varName = identCtx.getText();
            Token varNameToken = identCtx.start;

            if (symbolTable.containsSymbol(varName)) {
                JanderSemanticoUtils.addSemanticError(varNameToken, "identificador " + varName + " ja declarado anteriormente");
            } else {
                if (varJanderType != JanderType.INVALID) {
                    symbolTable.addSymbol(varName, varJanderType);
                } else {
                    JanderSemanticoUtils.addSemanticError(varNameToken, "tipo " + strTipoVar + " nao declarado");
                }
            }
        }
        return super.visitVariavel(ctx);
    }

    @Override
    public Void visitCmdAtribuicao(CmdAtribuicaoContext ctx) {
        String varName = ctx.identificador().getText();
        Token varNameToken = ctx.identificador().start;

        JanderSemanticoUtils.setCurrentAssignmentVariable(varName);
        JanderType expressionType = JanderSemanticoUtils.checkType(symbolTable, ctx.expressao());
        JanderSemanticoUtils.clearCurrentAssignmentVariableStack();

        if (expressionType != JanderType.INVALID) {
            if (!symbolTable.containsSymbol(varName)) {
                JanderSemanticoUtils.addSemanticError(varNameToken, "identificador " + varName + " nao declarado");
            } else {
                JanderType varType = symbolTable.getSymbolType(varName);
                if (JanderSemanticoUtils.areTypesIncompatible(varType, expressionType)) {
                    JanderSemanticoUtils.addSemanticError(varNameToken, "atribuicao nao compativel para " + varName);
                }
            }
        }
        return super.visitCmdAtribuicao(ctx);
    }

    @Override
    public Void visitCmdLeia(CmdLeiaContext ctx) {
        for (IdentificadorContext identCtx : ctx.identificador()) {
            String varName = identCtx.getText();
            Token varNameToken = identCtx.start;
            if (!symbolTable.containsSymbol(varName)) {
                JanderSemanticoUtils.addSemanticError(varNameToken, "identificador " + varName + " nao declarado");
            }
        }
        return super.visitCmdLeia(ctx);
    }

    @Override
    public Void visitParcela_nao_unario(Parcela_nao_unarioContext ctx) {
        if (ctx.identificador() != null || ctx.CADEIA() != null) {
            JanderSemanticoUtils.checkType(symbolTable, ctx);
        }
        return super.visitParcela_nao_unario(ctx);
    }

    @Override
    public Void visitParcela_unario(Parcela_unarioContext ctx) {
        if (ctx.identificador() != null || ctx.NUM_INT() != null || ctx.NUM_REAL() != null || ctx.expressao() != null || ctx.IDENT() != null) {
            JanderSemanticoUtils.checkType(symbolTable, ctx);
        }
        return super.visitParcela_unario(ctx);
    }

    private JanderType getJanderTypeFromString(String typeString, Token errorToken, boolean isUserType) {
        switch (typeString.toLowerCase()) {
            case "literal": return JanderType.LITERAL;
            case "inteiro": return JanderType.INTEGER;
            case "real": return JanderType.REAL;
            case "logico": return JanderType.LOGICAL;
            default:
                if (isUserType) {
                    if (!symbolTable.containsSymbol(typeString)) {
                        JanderSemanticoUtils.addSemanticError(errorToken, "tipo " + typeString + " nao declarado");
                        return JanderType.INVALID;
                    }
                    return symbolTable.getSymbolType(typeString);
                }
                JanderSemanticoUtils.addSemanticError(errorToken, "tipo " + typeString + " desconhecido");
                return JanderType.INVALID;
        }
    }

    private JanderType getJanderTypeFromGrammarTipo(TipoContext typeContextFromGrammar) {
        if (typeContextFromGrammar.tipo_estendido() != null) {
            Tipo_estendidoContext tec = typeContextFromGrammar.tipo_estendido();
            JanderType baseType;
            Token typeNameToken;

            if (tec.tipo_basico_ident().tipo_basico() != null) {
                String basicTypeName = tec.tipo_basico_ident().tipo_basico().getText();
                typeNameToken = tec.tipo_basico_ident().tipo_basico().getStart();
                baseType = getJanderTypeFromString(basicTypeName, typeNameToken, false);
            } else {
                String userTypeName = tec.tipo_basico_ident().IDENT().getText();
                typeNameToken = tec.tipo_basico_ident().IDENT().getSymbol();
                baseType = getJanderTypeFromString(userTypeName, typeNameToken, true);
            }

            if (baseType == JanderType.INVALID) {
                return JanderType.INVALID;
            }

            if (tec.getChild(0) instanceof org.antlr.v4.runtime.tree.TerminalNode &&
                tec.getChild(0).getText().equals("^")) {
                org.antlr.v4.runtime.Token caretTokenSymbol = ((org.antlr.v4.runtime.tree.TerminalNode) tec.getChild(0)).getSymbol();
                JanderSemanticoUtils.addSemanticError(caretTokenSymbol, "Tipos ponteiro (^) sao reconhecidos mas a semantica completa depende de melhorias. O tipo sera marcado como POINTER generico se disponivel, senao INVALIDO.");
                try {
                    return JanderType.valueOf("POINTER");
                } catch (IllegalArgumentException e) {
                    JanderSemanticoUtils.addSemanticError(caretTokenSymbol, "Tipo POINTER nao definido no sistema JanderType.");
                    return JanderType.INVALID;
                }
            }
            return baseType;

        } else if (typeContextFromGrammar.registro() != null) {
            JanderSemanticoUtils.addSemanticError(typeContextFromGrammar.registro().REGISTRO().getSymbol(), "Tipos registro ainda nao sao totalmente implementados. Assumido tipo INVALIDO.");
            return JanderType.INVALID;
        }
        JanderSemanticoUtils.addSemanticError(typeContextFromGrammar.getStart(), "Estrutura de tipo desconhecida na declaracao. Assumido tipo INVALIDO.");
        return JanderType.INVALID;
    }
}