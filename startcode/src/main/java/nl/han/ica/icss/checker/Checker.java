package nl.han.ica.icss.checker;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.*;
import nl.han.ica.icss.ast.types.ExpressionType;
import java.util.HashMap;

public class Checker {
    // Ik hou per scope bij welke variabelen bestaan en welk type ze hebben
    // Bovenaan (index 0) staat altijd de huidige scope
    private IHANLinkedList<HashMap<String, ExpressionType>> variabeleTypenPerScope;

    public void check(AST ast) {
        // Start elke check met een schone stack scopes
        variabeleTypenPerScope = new HANLinkedList<>();

        // Globale scope (stylesheet pmuch) eerst openen
        pushScope();

        // Dan de hele boom langs en overal controles doen
        visit(ast.root);

        // Klaar met de globale scope
        popScope();
    }

    // Scope helpers

    // Nieuwe scope erbij (bijv. bij het binnenlopen van een rule of if/else).
    private void pushScope() {
        variabeleTypenPerScope.addFirst(new HashMap<>());
    }

    // Klaar met de huidige scope en weer van de stapel af
    private void popScope() {
        if (variabeleTypenPerScope.getSize() > 0) variabeleTypenPerScope.removeFirst();
    }

    // Handige getter voor “bovenste” scope.
    private HashMap<String, ExpressionType> topScope() {
        return variabeleTypenPerScope.getFirst();
    }

    // Zoek het type van een variabele, beginnend bij de meest nabije scope
    private ExpressionType lookupVarType(String name) {
        for (int i = 0; i < variabeleTypenPerScope.getSize(); i++) {
            HashMap<String, ExpressionType> s = variabeleTypenPerScope.get(i);
            if (s.containsKey(name)) return s.get(name);
        }
        return ExpressionType.UNDEFINED;
    }

    // Variabele (opnieuw) vastleggen in de huidige scope.
    private void defineVar(String name, ExpressionType type) {
        topScope().put(name, type);
    }

    // AST-traversal openen/sluiten van scopes
    private void visit(ASTNode huidigKnooppunt) {
        if (huidigKnooppunt == null) return;

        // Scopes openen voorda we de kinderen langs gaan (pre-order)
        boolean scopeIsGeopendVoorDitKnooppunt = false;

        if (huidigKnooppunt instanceof Stylerule) {
            // Elke CSS regel krijgt zijn eigen scope
            pushScope();
            scopeIsGeopendVoorDitKnooppunt = true;

        } else if (huidigKnooppunt instanceof IfClause) {
            // If condition moet gewoon boolean zijn, zo niet dan fout op de if-knoop
            IfClause ifKnoop = (IfClause) huidigKnooppunt;
            ExpressionType typeVanVoorwaarde = typeOf(ifKnoop.conditionalExpression);
            if (typeVanVoorwaarde != ExpressionType.BOOL) {
                ifKnoop.setError("If-voorwaarde moet van het type boolean zijn (CH05).");
            }
            // Ook if-blokken hebben hun eigen scope.
            pushScope();
            scopeIsGeopendVoorDitKnooppunt = true;

        } else if (huidigKnooppunt instanceof ElseClause) {
            // Else blokken net zo goed.
            pushScope();
            scopeIsGeopendVoorDitKnooppunt = true;
        }

        // Node-specifieke checks

        if (huidigKnooppunt instanceof VariableAssignment) {
            // eerst type bepalen van de rechterkant
            VariableAssignment variabeleToekenning = (VariableAssignment) huidigKnooppunt;

            // In deze AST is de waarde meestal het laatste kind
            Expression rechterZijdeWaarde = null;
            if (!variabeleToekenning.getChildren().isEmpty()) {
                ASTNode laatsteKind =
                        variabeleToekenning.getChildren().get(variabeleToekenning.getChildren().size() - 1);
                if (laatsteKind instanceof Expression) {
                    rechterZijdeWaarde = (Expression) laatsteKind;
                }
            }

            ExpressionType typeVanRechterZijde = typeOf(rechterZijdeWaarde);

            // Geen type? Dan mis ik een definitie of klopt er iets niet in de expressie die ik heb neergezet
            if (typeVanRechterZijde == ExpressionType.UNDEFINED) {
                variabeleToekenning.setError(
                        "Rechterkant van variabele '" + variabeleToekenning.name.name
                                + "' heeft een onbekend (undefined) type (CH01/CH06)."
                );
            }

            // Variabele direct vastleggen in de huidige scope
            defineVar(variabeleToekenning.name.name, typeVanRechterZijde);

        } else if (huidigKnooppunt instanceof Declaration) {
            // Declaraties (property: value): check of de value past bij de property
            Declaration declaratie = (Declaration) huidigKnooppunt;

            // Alleen checken als er ook echt een waarde (expression) staat
            if (!declaratie.getChildren().isEmpty()
                    && declaratie.getChildren().get(0) instanceof Expression) {

                Expression waarde = (Expression) declaratie.getChildren().get(0);
                ExpressionType typeVanWaarde = typeOf(waarde);

                // Property naam pak ik als string (PropertyName is hier geen enum).
                String eigenschapTekst = (declaratie.property == null)
                        ? ""
                        : declaratie.property.toString().toLowerCase(java.util.Locale.ROOT);

                // Alleen deze properties zijn toegestaan. Type moet kloppen
                if ("color".equals(eigenschapTekst) || "background-color".equals(eigenschapTekst)) {
                    if (typeVanWaarde != ExpressionType.COLOR) {
                        declaratie.setError("Eigenschap '" + eigenschapTekst + "' verwacht een kleurwaarde (CH04).");
                    }
                } else if ("width".equals(eigenschapTekst) || "height".equals(eigenschapTekst)) {
                    boolean isToegestaneLengte =
                            (typeVanWaarde == ExpressionType.PIXEL || typeVanWaarde == ExpressionType.PERCENTAGE);
                    if (!isToegestaneLengte) {
                        declaratie.setError(
                                "Eigenschap '" + eigenschapTekst + "' verwacht een pixel- of percentagewaarde (CH04)."
                        );
                    }
                } else {
                    // Alles buiten de whitelist is in ICSS niet toegestaan
                    declaratie.setError("Eigenschap '" + eigenschapTekst + "' is niet toegestaan in ICSS.");
                }
            }
        }

        // Kinderen bezoeken (diep de boom in)
        for (ASTNode kind : huidigKnooppunt.getChildren()) {
            visit(kind);
        }

        // Scopes weer sluiten nadat we de kinderen gehad hebben (post-order).
        if (scopeIsGeopendVoorDitKnooppunt) {
            popScope();
        }
    }

    // Typebepaling voor expressies + regels CH02/CH03
    private ExpressionType typeOf(Expression expressie) {
        if (expressie == null) return ExpressionType.UNDEFINED;

        // Literals hebben een vast type; klaar.
        if (expressie instanceof PixelLiteral)      return ExpressionType.PIXEL;
        if (expressie instanceof PercentageLiteral) return ExpressionType.PERCENTAGE;
        if (expressie instanceof ScalarLiteral)     return ExpressionType.SCALAR;
        if (expressie instanceof ColorLiteral)      return ExpressionType.COLOR;
        if (expressie instanceof BoolLiteral)       return ExpressionType.BOOL;

        // type komt uit de scope (anders undefined en foutje zetten).
        if (expressie instanceof VariableReference) {
            String variabeleNaam = ((VariableReference) expressie).name;
            ExpressionType gevondenType = lookupVarType(variabeleNaam);
            if (gevondenType == ExpressionType.UNDEFINED) {
                expressie.setError("Gebruik van ongedefinieerde variabele '" + variabeleNaam + "' (CH01/CH06).");
            }
            return gevondenType;
        }

        // Optellen/Aftrekken:
        // * Geen kleuren in rekensommen (CH03)
        // * Operanden moeten gelijk type hebben en niet boolaen zijn (CH02)
        if (expressie instanceof AddOperation || expressie instanceof SubtractOperation) {
            Operation bewerking = (Operation) expressie;
            ExpressionType linkerType = typeOf(bewerking.lhs);
            ExpressionType rechterType = typeOf(bewerking.rhs);

            if (linkerType == ExpressionType.COLOR || rechterType == ExpressionType.COLOR) {
                expressie.setError("Kleurwaarden mogen niet gebruikt worden in + of - (CH03).");
                return ExpressionType.UNDEFINED;
            }

            boolean typesZijnGelijk = (linkerType == rechterType);
            boolean isBooleanBijOperand = (linkerType == ExpressionType.BOOL || rechterType == ExpressionType.BOOL);
            if (!typesZijnGelijk || isBooleanBijOperand) {
                expressie.setError("Beide operanden van + en− moeten hetzelfde, niet boolean type hebben (CH02).");
                return ExpressionType.UNDEFINED;
            }
            return linkerType; // types zijn gelijk, dus dit is het resultaat
        }

        // Vermenigvuldigen:
        // * Geen kleuren (CH03)
        // * Minstens eenn operand scalar, geen booleans (CH02)
        if (expressie instanceof MultiplyOperation) {
            Operation bewerking = (Operation) expressie;
            ExpressionType linkerType = typeOf(bewerking.lhs);
            ExpressionType rechterType = typeOf(bewerking.rhs);

            if (linkerType == ExpressionType.COLOR || rechterType == ExpressionType.COLOR) {
                expressie.setError("Kleurwaarden mogen niet gebruikt worden in * (komt van CH03).");
                return ExpressionType.UNDEFINED;
            }

            boolean bevatBoolean = (linkerType == ExpressionType.BOOL || rechterType == ExpressionType.BOOL);
            boolean heeftMinstensEenScalar =
                    (linkerType == ExpressionType.SCALAR || rechterType == ExpressionType.SCALAR);

            if (bevatBoolean || !heeftMinstensEenScalar) {
                expressie.setError("Bij vermenigvuldigen moet minstens een operand SCALAR zijn en geen van beide BOOL/KLEUR (CH02).");
                return ExpressionType.UNDEFINED;
            }

            // Resultaat is het niet-scalar type (of scalar als beide scalar zijn).
            if (linkerType == ExpressionType.SCALAR && rechterType == ExpressionType.SCALAR) {
                return ExpressionType.SCALAR;
            }
            return (linkerType == ExpressionType.SCALAR) ? rechterType : linkerType;
        }

        // Geen idee? Dan ga ik uit van undefined (checker houdt je hier wel tegen).
        return ExpressionType.UNDEFINED;
    }
}

/*
Voorbeelden die ik gebruikte om te testen (moet parse slagen, maar check juist rode errors geven):

UseLinkColor := 3px;

a {
  color: 12px;        // fout: color verwacht een kleur (#rrggbb)
  if [UseLinkColor] { // fout: if-voorwaarde moet boolean zijn
    width: 10px;
  }
}

UseLinkColor := 3px;

a {
  color: 12px;
  if [UseLinkColor] {
    width: 10px;
  }
}
*/
