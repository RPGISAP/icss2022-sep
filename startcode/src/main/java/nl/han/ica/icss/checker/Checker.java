package nl.han.ica.icss.checker;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.*;
import nl.han.ica.icss.ast.types.ExpressionType;
import java.util.HashMap;

public class Checker {
    private IHANLinkedList<HashMap<String, ExpressionType>> variabeleTypenPerScope;

    public void check(AST ast) {
        variabeleTypenPerScope = new HANLinkedList<>();
        pushScope();
        visit(ast.root);
        popScope();
    }

    // ====== Scope helpers ======
    private void pushScope() {
        variabeleTypenPerScope.addFirst(new HashMap<>());
    }

    private void popScope() {
        if (variabeleTypenPerScope.getSize() > 0) variabeleTypenPerScope.removeFirst();
    }

    private HashMap<String, ExpressionType> topScope() {
        return variabeleTypenPerScope.getFirst();
    }

    private ExpressionType lookupVarType(String name) {
        for (int i = 0; i < variabeleTypenPerScope.getSize(); i++) {
            HashMap<String, ExpressionType> s = variabeleTypenPerScope.get(i);
            if (s.containsKey(name)) return s.get(name);
        }
        return ExpressionType.UNDEFINED;
    }

    private void defineVar(String name, ExpressionType type) {
        topScope().put(name, type);
    }

    // ====== AST-traversal & scope-afhandeling ======
    private void visit(ASTNode huidigKnooppunt) {
        if (huidigKnooppunt == null) return;

        // ---- Scopes openen (pre-order) ----
        boolean scopeIsGeopendVoorDitKnooppunt = false;

        if (huidigKnooppunt instanceof Stylerule) {
            // Nieuwe CSS-regel wordt nieuwe scope
            pushScope();
            scopeIsGeopendVoorDitKnooppunt = true;

        } else if (huidigKnooppunt instanceof IfClause) {
            // CH05: conditie van if moet boolean zijn
            IfClause ifKnoop = (IfClause) huidigKnooppunt;
            ExpressionType typeVanVoorwaarde = typeOf(ifKnoop.conditionalExpression);
            if (typeVanVoorwaarde != ExpressionType.BOOL) {
                ifKnoop.setError("If-voorwaarde moet van het type boolean zijn (CH05).");
            }

            // Scope voor het if-blok openen
            pushScope();
            scopeIsGeopendVoorDitKnooppunt = true;

        } else if (huidigKnooppunt instanceof ElseClause) {
            // Scope voor het else-blok
            pushScope();
            scopeIsGeopendVoorDitKnooppunt = true;
        }

        // ---- Node-specifieke controles/acties ----
        if (huidigKnooppunt instanceof VariableAssignment) {

            VariableAssignment variabeleToekenning = (VariableAssignment) huidigKnooppunt;

            // Pak de waarde rechts (RHS). In deze AST is dat het laatste kind.
            Expression rechterZijdeWaarde = null;
            if (!variabeleToekenning.getChildren().isEmpty()) {
                ASTNode laatsteKind = variabeleToekenning
                        .getChildren()
                        .get(variabeleToekenning.getChildren().size() - 1);
                if (laatsteKind instanceof Expression) {
                    rechterZijdeWaarde = (Expression) laatsteKind;
                }
            }
            ExpressionType typeVanRechterZijde = typeOf(rechterZijdeWaarde);
            if (typeVanRechterZijde == ExpressionType.UNDEFINED) {
                variabeleToekenning.setError(
                        "Rechterkant van variabele '" + variabeleToekenning.name.name
                                + "' heeft een onbekend (undefined) type (CH01/CH06)."
                );
            }

            // CH06: variabele in de huidige scope registreren
            defineVar(variabeleToekenning.name.name, typeVanRechterZijde);

        } else if (huidigKnooppunt instanceof Declaration) {

            Declaration declaratie = (Declaration) huidigKnooppunt;

            // Alleen checken als er een waarde is en die een Expression is
            if (!declaratie.getChildren().isEmpty()
                    && declaratie.getChildren().get(0) instanceof Expression) {

                Expression waarde = (Expression) declaratie.getChildren().get(0);
                ExpressionType typeVanWaarde = typeOf(waarde);

                // Eigenschapsnaam als tekst (PropertyName is blijkbaar geen enum, dus stringify (kannie herrineren als ik dit gedaan heb of niet ik neem geen kansen)
                String eigenschapTekst = (declaratie.property == null)
                        ? ""
                        : declaratie.property.toString().toLowerCase(java.util.Locale.ROOT);

                // CH04: type van de value moet passen bij de property
                if ("color".equals(eigenschapTekst)) {
                    if (typeVanWaarde != ExpressionType.COLOR) {
                        declaratie.setError("Eigenschap 'color' verwacht een kleurwaarde (CH04).");
                    }
                } else if ("width".equals(eigenschapTekst) || "height".equals(eigenschapTekst)) {
                    boolean isToegestaneLengte =
                            (typeVanWaarde == ExpressionType.PIXEL
                                    || typeVanWaarde == ExpressionType.PERCENTAGE);
                    if (!isToegestaneLengte) {
                        declaratie.setError(
                                "Eigenschap '" + eigenschapTekst
                                        + "' verwacht een pixel- of percentagewaarde (CH04)."
                        );
                    }
                }
            }
        }

        // ---- child nodes kijken ----
        for (ASTNode kind : huidigKnooppunt.getChildren()) {
            visit(kind);
        }

        // ---- Scopes sluiten (post-order) ----
        if (scopeIsGeopendVoorDitKnooppunt) {
            popScope();
        }
    }

    // ====== Typebepaling van de expressies en CH02/CH03 ======
    private ExpressionType typeOf(Expression expressie) {
        if (expressie == null) return ExpressionType.UNDEFINED;

        // Literal-types
        if (expressie instanceof PixelLiteral)      return ExpressionType.PIXEL;
        if (expressie instanceof PercentageLiteral) return ExpressionType.PERCENTAGE;
        if (expressie instanceof ScalarLiteral)     return ExpressionType.SCALAR;
        if (expressie instanceof ColorLiteral)      return ExpressionType.COLOR;
        if (expressie instanceof BoolLiteral)       return ExpressionType.BOOL;

        // Variabele-referentie
        if (expressie instanceof VariableReference) {
            String variabeleNaam = ((VariableReference) expressie).name;
            ExpressionType gevondenType = lookupVarType(variabeleNaam);
            if (gevondenType == ExpressionType.UNDEFINED) {
                expressie.setError("Gebruik van ongedefinieerde variabele '" + variabeleNaam + "' (CH01/CH06).");
            }
            return gevondenType;
        }

        // Optelling/Aftrekking (CH02, CH03)
        if (expressie instanceof AddOperation || expressie instanceof SubtractOperation) {
            Operation bewerking = (Operation) expressie;
            ExpressionType linkerType = typeOf(bewerking.lhs);
            ExpressionType rechterType = typeOf(bewerking.rhs);

            // CH03: geen kleuren in rekenoperaties
            if (linkerType == ExpressionType.COLOR || rechterType == ExpressionType.COLOR) {
                expressie.setError("Kleurwaarden mogen niet gebruikt worden in + of - (CH03).");
                return ExpressionType.UNDEFINED;
            }

            // CH02: +/− alleen als types gelijk zijn en niet-boolean
            boolean typesZijnGelijk = (linkerType == rechterType);
            boolean isBooleanBijOperand = (linkerType == ExpressionType.BOOL || rechterType == ExpressionType.BOOL);
            if (!typesZijnGelijk || isBooleanBijOperand) {
                expressie.setError("Beide operanden van +/− moeten hetzelfde, niet-boolean type hebben (CH02).");
                return ExpressionType.UNDEFINED;
            }
            // Resultaattype is het (gelijke) operanden-type
            return linkerType;
        }

        // Vermenigvuldiging (van CH02, CH03)
        if (expressie instanceof MultiplyOperation) {
            Operation bewerking = (Operation) expressie;
            ExpressionType linkerType = typeOf(bewerking.lhs);
            ExpressionType rechterType = typeOf(bewerking.rhs);

            // CH03: geen kleuren in rekenoperaties
            if (linkerType == ExpressionType.COLOR || rechterType == ExpressionType.COLOR) {
                expressie.setError("Kleurwaarden mogen niet gebruikt worden in * (komt van CH03).");
                return ExpressionType.UNDEFINED;
            }

            // CH02: minstens eenn operand moet SCALAR zijn. geen BOOL
            boolean bevatBoolean = (linkerType == ExpressionType.BOOL || rechterType == ExpressionType.BOOL);
            boolean heeftMinstensEenScalar = (linkerType == ExpressionType.SCALAR || rechterType == ExpressionType.SCALAR);
            if (bevatBoolean || !heeftMinstensEenScalar) {
                expressie.setError("Bij vermenigvuldigen moet minstens één operand SCALAR zijn en geen van beide BOOL/KLEUR (CH02).");
                return ExpressionType.UNDEFINED;
            }

            // Resultaat: het niet-SCALAR type
            if (linkerType == ExpressionType.SCALAR && rechterType == ExpressionType.SCALAR) {
                return ExpressionType.SCALAR;
            }
            return (linkerType == ExpressionType.SCALAR) ? rechterType : linkerType;
        }

        // Onbekend/complex = geen type
        return ExpressionType.UNDEFINED;
    }
}

//VOORBEELDEN WAAR IK MEE GETEST HEB WAAR DE PARSE GEHAALD WORDT MAAR NIET DE CHECKER:
//UseLinkColor := 3px;
//
//a {
//  color: 12px;
//  if [UseLinkColor] {
//    width: 10px;
//  }
//}

//UseLinkColor := 3px;
//
//a {
//  color: 12px;
//  if [UseLinkColor] {
//    width: 10px;
//  }
//}