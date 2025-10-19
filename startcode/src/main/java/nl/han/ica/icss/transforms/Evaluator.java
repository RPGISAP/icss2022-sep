package nl.han.ica.icss.transforms;

import nl.han.ica.datastructures.HANLinkedList;
import nl.han.ica.datastructures.IHANLinkedList;
import nl.han.ica.icss.ast.AST;
import nl.han.ica.icss.ast.Literal;

import java.util.HashMap;

public class Evaluator implements Transform {

    private IHANLinkedList<HashMap<String, Literal>> variabeleWaarden;

    public Evaluator() {
        variabeleWaarden = new HANLinkedList<>();
    }

    @Override
    public void apply(AST ast) {
        variabeleWaarden = new HANLinkedList<>();
        // Later: evaluatie van expressies en if/else uitvoeren.
    }
}

