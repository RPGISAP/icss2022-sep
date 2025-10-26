package nl.han.ica.icss.generator;

import nl.han.ica.icss.ast.AST;
import nl.han.ica.icss.ast.ASTNode;
import nl.han.ica.icss.ast.Declaration;
import nl.han.ica.icss.ast.Expression;
import nl.han.ica.icss.ast.Stylesheet;
import nl.han.ica.icss.ast.Stylerule;
import nl.han.ica.icss.ast.selectors.ClassSelector;
import nl.han.ica.icss.ast.selectors.IdSelector;
import nl.han.ica.icss.ast.Selector;
import nl.han.ica.icss.ast.selectors.TagSelector;
import nl.han.ica.icss.ast.literals.BoolLiteral;
import nl.han.ica.icss.ast.literals.ColorLiteral;
import nl.han.ica.icss.ast.literals.PercentageLiteral;
import nl.han.ica.icss.ast.literals.PixelLiteral;
import nl.han.ica.icss.ast.literals.ScalarLiteral;

public class Generator {

	public String generate(AST ast) {
// Geen AST? Geen output.
		if (ast == null || ast.root == null) return "";

		// We bouwen hier de uiteindelijke CSS-string in op.
		StringBuilder css = new StringBuilder();

		// Start bij het stylesheet en loop alles netjes af.
		genereerStylesheet(ast.root, css, 0);
		return css.toString();
	}

	// Loopt over de bovenste laag (stylesheet) en pakt alleen stylerules mee.
	private void genereerStylesheet(Stylesheet stijlblad, StringBuilder css, int inspringNiveau) {
		for (ASTNode knoop : stijlblad.body) {
			if (knoop instanceof Stylerule) {
				genereerRegel((Stylerule) knoop, css, inspringNiveau);
			}
		}
	}

	// Schrijft één hele CSS-regel (selectors + blok met declaraties).
	private void genereerRegel(Stylerule stijlregel, StringBuilder css, int inspringNiveau) {
		// Selectors aan elkaar plakken (met comma’s ertussen).
		StringBuilder selectorTekst = new StringBuilder();
		for (int i = 0; i < stijlregel.selectors.size(); i++) {
			if (i > 0) selectorTekst.append(", ");
			selectorTekst.append(selectorNaarTekst(stijlregel.selectors.get(i)));
		}

		// Kop van de regel: "a, .menu {"
		inspring(css, inspringNiveau).append(selectorTekst).append(" {\n");

		// De declaraties binnen de regel (ingesprongen).
		for (ASTNode child : stijlregel.body) {
			if (child instanceof Declaration) {
				genereerDeclaratie((Declaration) child, css, inspringNiveau + 1);
			}
		}

		// Sluit de regel af met een accolade.
		inspring(css, inspringNiveau).append("}\n");
	}

	// Schrijft één "property: value;" regel.
	private void genereerDeclaratie(Declaration declaratie, StringBuilder css, int inspringNiveau) {
		// Pak de echte property-naam (zit in 'name', niet via toString()).
		String propertyNaam = (declaratie.property != null) ? declaratie.property.name : "";
		String waardeTekst  = literalNaarCss(declaratie.expression);

		inspring(css, inspringNiveau)
				.append(propertyNaam)
				.append(": ")
				.append(waardeTekst)
				.append(";\n");
	}

	// Zet een Selector om naar de juiste CSS-tekst (#id, .class, of tag).
	private String selectorNaarTekst(Selector selector) {
		if (selector instanceof IdSelector) {
			return "#" + ((IdSelector) selector).id;
		}
		if (selector instanceof ClassSelector) {
			return "." + ((ClassSelector) selector).cls;
		}
		if (selector instanceof TagSelector) {
			return ((TagSelector) selector).tag;
		}
		// Fallback (zou eigenlijk niet moeten gebeuren).
		return selector.toString();
	}

	// Zet een Literal om naar CSS (na transform zijn values Literal’s).
	private String literalNaarCss(Expression expressie) {
		if (expressie instanceof PixelLiteral) {
			return ((PixelLiteral) expressie).value + "px";
		}
		if (expressie instanceof PercentageLiteral) {
			return ((PercentageLiteral) expressie).value + "%";
		}
		if (expressie instanceof ColorLiteral) {
			return ((ColorLiteral) expressie).value; // bijv. "#ff0000"
		}
		if (expressie instanceof BoolLiteral) {
			return ((BoolLiteral) expressie).value ? "TRUE" : "FALSE";
		}
		if (expressie instanceof ScalarLiteral) {
			return Integer.toString(((ScalarLiteral) expressie).value);
		}
		// Niks of onbekend? Dan maar leeg.
		return expressie == null ? "" : expressie.toString();
	}

	// Twee spaties per inspring-niveau (zoals in de eisen staat).
	private StringBuilder inspring(StringBuilder css, int niveaus) {
		for (int i = 0; i < niveaus; i++) {
			css.append("  ");
		}
		return css;
	}
}


