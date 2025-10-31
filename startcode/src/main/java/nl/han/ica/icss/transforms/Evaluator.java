package nl.han.ica.icss.transforms;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Evaluator implements Transform {

    // Ik hou een stack van scopes bij. Bovenaan ligt altijd de huidige scope.
    // In de scope map ik variabele namen naar Literalwaardes.
    private IHANLinkedList<HashMap<String, Literal>> stackScopes = new HANLinkedList<>();

    @Override
    public void apply(AST ast) {
        // Start helemaal vers met een lege stack.
        stackScopes = new HANLinkedList<>();

        // Eerst open ik een globale scope (geldt voor de hele stylesheet).
        openNieuweScope();

        // een rondje door de boom waarin ik alles doe:
        // - expressies uitrekenen en vervangen door literals (TR01)
        // - if/else uitvouwen en de IfClause knoop weghalen (TR02)
        transformeerKinderen(ast.root);

        // Klaar met de globale scope.
        sluitHuidigeScope();
    }

    // Scope helper dingetjes
    private void openNieuweScope() {
        stackScopes.addFirst(new HashMap<>());
    }

    private void sluitHuidigeScope() {
        if (stackScopes.getSize() > 0) {
            stackScopes.removeFirst();
        }
    }

    private void definieerVariabele(String naam, Literal waarde) {
        // Gewoon in de bovenste (huidige) scope zetten of overschrijven.
        stackScopes.getFirst().put(naam, waarde);
    }

    private Literal zoekVariabele(String naam) {
        // Altijd eerst de huidige (bovenste) scope proberen.
        try {
            HashMap<String, Literal> top = stackScopes.getFirst();
            if (top != null && top.containsKey(naam)) {
                return top.get(naam);
            }
        } catch (Exception ignored) {
            // geen scopes
        }

        // 2) Dan de overige scopes van binnen daarna buiten.
        int n = stackScopes.getSize();
        for (int i = n - 1; i >= 0; i--) {
            HashMap<String, Literal> scope = stackScopes.get(i);
            if (scope != null && scope.containsKey(naam)) {
                return scope.get(naam);
            }
        }
        return null;
    }


    // Deku tree Traversal & transformaties

    // Ik verwerk de “echte” body-lijst van een knoop (dus de lijst die ik mag aanpassen).
    // Daarin kan ik knopen vervangen/verwijderen/invoegen zonder gedoe.
    private void transformeerKinderen(ASTNode ouderKnoop) {
        List<ASTNode> bewerkbareLijst = modificeerbareBodyVan(ouderKnoop);

        if (bewerkbareLijst != null) {
            // Index gestuurde forloop zodat ik tijdens het lopen veilig kan splicen.
            for (int index = 0; index < bewerkbareLijst.size(); index++) {
                ASTNode huidigeKnoop = bewerkbareLijst.get(index);

                // Elke stylerule krijgt gewoon een eigen scope.
                if (huidigeKnoop instanceof Stylerule) {
                    openNieuweScope();
                    transformeerKinderen(huidigeKnoop);
                    sluitHuidigeScope();
                    continue;
                }

                // IfClause uitvouwen:
                // - conditie uitrekenen
                // - if-body of else-body kiezen
                // - IfClause zelf weghalen en vervangen door die gekozen body
                if (huidigeKnoop instanceof IfClause) {
                    IfClause ifKnoop = (IfClause) huidigeKnoop;

                    // Conditie evalueren
                    Literal voorwaarde = evalueerExpressie(ifKnoop.conditionalExpression);
                    boolean isWaar = (voorwaarde instanceof BoolLiteral)
                            && ((BoolLiteral) voorwaarde).value;

                    // Body kiezen
                    List<ASTNode> gekozenBody = isWaar
                            ? new ArrayList<>(ifKnoop.body)
                            : (ifKnoop.elseClause != null
                            ? new ArrayList<>(ifKnoop.elseClause.body)
                            : new ArrayList<>());

                    //Eerst splicen:
                    bewerkbareLijst.remove(index);

                    if (!gekozenBody.isEmpty()) {
                        bewerkbareLijst.addAll(index, gekozenBody);
                        // Laat de hoofdloop de nieuw ingevoegde knopen bezoeken
                        index -= 1;
                    } else {
                        // Hele if vervalt: stap eenn terug zodat volgende item netjes bezocht wordt
                        index -= 1;
                    }
                    continue;
                }

                // Losse ElseClause zou eigenlijk niet hier moeten staan (normaal alleen als deel van IfClause),
                // maar voor de zekerheid verwerk ik gewoon zijn kinderen.
                if (huidigeKnoop instanceof ElseClause) {
                    transformeerKinderen(huidigeKnoop);
                    continue;
                }

                // Variabele assignment:
                // - rechterkant uitrekenen naar een Literal
                // - Literal terugzetten in de knoop
                // - variabele in de huidige scope registreren
                if (huidigeKnoop instanceof VariableAssignment) {
                    VariableAssignment toekenning = (VariableAssignment) huidigeKnoop;
                    Literal berekendeWaarde = evalueerExpressie(toekenning.expression);
                    toekenning.expression = berekendeWaarde;
                    definieerVariabele(toekenning.name.name, berekendeWaarde);
                    transformeerKinderen(huidigeKnoop); // er kunnen theoretisch nog kinderen onder hangen
                    continue;
                }

                // Declaration:
                // - de value (expression) uitrekenen en vervangen door een Literal
                if (huidigeKnoop instanceof Declaration) {
                    Declaration declaratie = (Declaration) huidigeKnoop;
                    if (declaratie.expression != null) {
                        declaratie.expression = evalueerExpressie(declaratie.expression);
                    }
                    transformeerKinderen(huidigeKnoop);
                    continue;
                }

                // Voor alle andere knopen gewoon dieper de boom in.
                transformeerKinderen(huidigeKnoop);
            }
            return;
        }

        // Als een knoop geen “modificeerbare body” heeft (bv. een literal of selector),
        // loop ik gewoon over de children.
        for (ASTNode kind : ouderKnoop.getChildren()) {
            transformeerKinderen(kind);
        }
    }

    // Dit geeft me de echte ArrayList terug die ik mag aanpassen
    // (dus niet een kopie) voor de knopen die dat soort body hebben.
    private List<ASTNode> modificeerbareBodyVan(ASTNode knoop) {
        if (knoop instanceof Stylesheet) return ((Stylesheet) knoop).body;
        if (knoop instanceof Stylerule)  return ((Stylerule)  knoop).body;
        if (knoop instanceof IfClause)   return ((IfClause)   knoop).body;
        if (knoop instanceof ElseClause) return ((ElseClause) knoop).body;
        return null;
    }

    // De Expressies uitrekenen

    // Ik reken een Expression uit en geef een Literal terug (Pixel/Percentage/Scalar/Color/Bool in dit geval).
    private Literal evalueerExpressie(Expression expressie) {
        if (expressie == null) return new ScalarLiteral(0);

        // Als het al een Literal is, ben ik klaar.
        if (expressie instanceof PixelLiteral)      return (PixelLiteral) expressie;
        if (expressie instanceof PercentageLiteral) return (PercentageLiteral) expressie;
        if (expressie instanceof ScalarLiteral)     return (ScalarLiteral) expressie;
        if (expressie instanceof ColorLiteral)      return (ColorLiteral) expressie;
        if (expressie instanceof BoolLiteral)       return (BoolLiteral) expressie;

        // Variabele-referentie, pak de huidige waarde uit de scopes.
        if (expressie instanceof VariableReference) {
            String naam = ((VariableReference) expressie).name;
            Literal gevonden = zoekVariabele(naam);
            // Als het niet gevonden is, val ik terug op iets veiligs.
            return (gevonden != null) ? gevonden : new ScalarLiteral(0);
        }

        // Optellen / aftrekken: eerst links en rechts uitrekenen, daarna combineren.
        if (expressie instanceof AddOperation || expressie instanceof SubtractOperation) {
            Operation operatie = (Operation) expressie;
            Literal links  = evalueerExpressie(operatie.lhs);
            Literal rechts = evalueerExpressie(operatie.rhs);
            boolean isPlus = expressie instanceof AddOperation;
            return telOfTrekAf(links, rechts, isPlus);
        }

        // Vermenigvuldigen: zelfde verhaal, eerst links/rechts uitrekenen.
        if (expressie instanceof MultiplyOperation) {
            Operation operatie = (Operation) expressie;
            Literal links  = evalueerExpressie(operatie.lhs);
            Literal rechts = evalueerExpressie(operatie.rhs);
            return vermenigvuldig(links, rechts);
        }

        // Onbekend type? Dan speel ik safe met 0.
        return new ScalarLiteral(0);
    }

    // Rekenregels voor + en -:
    // Alleen “hetzelfde” optellen/afrekken (px met px, % met %, scalar met scalar).
    private Literal telOfTrekAf(Literal links, Literal rechts, boolean isOptellen) {
        if (links instanceof PixelLiteral && rechts instanceof PixelLiteral) {
            int a = ((PixelLiteral) links).value;
            int b = ((PixelLiteral) rechts).value;
            return new PixelLiteral(isOptellen ? a + b : a - b);
        }
        if (links instanceof PercentageLiteral && rechts instanceof PercentageLiteral) {
            int a = ((PercentageLiteral) links).value;
            int b = ((PercentageLiteral) rechts).value;
            return new PercentageLiteral(isOptellen ? a + b : a - b);
        }
        if (links instanceof ScalarLiteral && rechts instanceof ScalarLiteral) {
            int a = ((ScalarLiteral) links).value;
            int b = ((ScalarLiteral) rechts).value;
            return new ScalarLiteral(isOptellen ? a + b : a - b);
        }
        // Andere combinaties komen normaal niet langs (checker blokkeert dat),
        // maar als het toch gebeurt, houd ik het bij 0.
        return new ScalarLiteral(0);
    }

    // Rekenregels voor *:
    // Minstens eenn kant moet scalar zijn
    private Literal vermenigvuldig(Literal links, Literal rechts) {
        if (links instanceof ScalarLiteral && rechts instanceof ScalarLiteral) {
            return new ScalarLiteral(((ScalarLiteral) links).value * ((ScalarLiteral) rechts).value);
        }
        if (links instanceof PixelLiteral && rechts instanceof ScalarLiteral) {
            return new PixelLiteral(((PixelLiteral) links).value * ((ScalarLiteral) rechts).value);
        }
        if (links instanceof ScalarLiteral && rechts instanceof PixelLiteral) {
            return new PixelLiteral(((ScalarLiteral) links).value * ((PixelLiteral) rechts).value);
        }
        if (links instanceof PercentageLiteral && rechts instanceof ScalarLiteral) {
            return new PercentageLiteral(((PercentageLiteral) links).value * ((ScalarLiteral) rechts).value);
        }
        if (links instanceof ScalarLiteral && rechts instanceof PercentageLiteral) {
            return new PercentageLiteral(((ScalarLiteral) links).value * ((PercentageLiteral) rechts).value);
        }
        // Alles wat hier niet in past behandel ik weer als “doe maar 0”.
        return new ScalarLiteral(0);
    }
}
