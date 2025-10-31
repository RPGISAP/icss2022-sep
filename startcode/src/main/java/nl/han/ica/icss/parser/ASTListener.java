package nl.han.ica.icss.parser;

import nl.han.ica.datastructures.HANStack;
import nl.han.ica.datastructures.IHANStack;
import nl.han.ica.icss.ast.AST;
import nl.han.ica.icss.ast.ASTNode;
import nl.han.ica.icss.ast.Expression;
import nl.han.ica.icss.ast.VariableReference;
import nl.han.ica.icss.ast.*;
import nl.han.ica.icss.ast.selectors.*;
import nl.han.ica.icss.ast.literals.*;
import nl.han.ica.icss.ast.operations.*;

public class ASTListener extends ICSSBaseListener {

	private final AST ast;
	private final IHANStack<ASTNode> containerStack;
	private final HANStack<Expression> expressieStack = new HANStack<>();
	private final HANStack<Integer> frameStack = new HANStack<>();
	private int expressieStackGrootte = 0;
	private boolean elseActief = false;
	private boolean inIfVoorwaarde = false;
	private StringBuilder ifCondBuffer = new StringBuilder();

	public ASTListener() {
		this.ast = new AST();  // Maak een nieuwe, lege AST aan.
		this.containerStack = new HANStack<>();  // Stack klaarzetten voor het “hangen” van knopen.
		System.out.println("ASTListener geladen van: " +
				ASTListener.class.getProtectionDomain().getCodeSource().getLocation());
	}


	public AST getAST() {
		return ast;
	}

	// Push een knoop op de container-stack (wordt de “huidige ouder”).
	private void push(ASTNode node) {
		containerStack.push(node);
	}

	// Haal de bovenste knoop van de container-stack (klaar met deze container).
	private ASTNode pop() {
		return containerStack.pop();
	}

	// Kijk naar de bovenste knoop (zonder eraf te halen).
	private ASTNode top() {
		return containerStack.peek();
	}

	// Voeg een kind toe aan de huidige ouder (bovenste container op de stack).
	private void hangAanOuder(ASTNode kind) {
		if (kind == null) return;   // voorkom null-kind in AST
		top().addChild(kind);
	}

	// Start een “frame” voor expressies (onthoud vanaf welke index nieuwe termen/factoren komen).
	private void startFrame() {
		frameStack.push(expressieStackGrootte);
	}

	// Haal het start-index van het huidige frame op.
	private int eindFrameIndex() {
		return frameStack.pop();
	}

	// Push een losse expressie (literal, var-ref, bewerking) op de expressie stack
	private void exprPush(Expression e) {
		expressieStack.push(e);
		expressieStackGrootte++;
	}

	// Pop een expressie van de stack.
	private Expression exprPop() {
		Expression e = expressieStack.pop();
		expressieStackGrootte--;
		return e;
	}

	// Haal alle expressies op die sinds het gegeven frame-index zijn gepusht (in volgorde links naar rechts).
	private java.util.List<Expression> pakOperandenSindsFrame(int indexVoor) {
		int aantal = expressieStackGrootte - indexVoor;
		java.util.List<Expression> exprs = new java.util.ArrayList<>(aantal);
		for (int i = 0; i < aantal; i++) {
			// pop in LIFO, vooraan toevoegen om volgorde links naar rechts te behouden
			exprs.add(0, exprPop());
		}
		return exprs;
	}

	//Stylesheet begginings
	@Override
	public void enterStylesheet(ICSSParser.StylesheetContext ctx) {
		Stylesheet sheet = new Stylesheet(); // Nieuwe root-container voor alles.
		ast.setRoot(sheet);
		push(sheet); 						// Vanaf nu zijn dit je ouders.
	}

	@Override
	public void exitStylesheet(ICSSParser.StylesheetContext ctx) {
		pop(); // klaar met de root, stack leegmaken.
	}

	//Rulset begginings
	@Override
	public void enterRuleset(ICSSParser.RulesetContext ctx) {
		Stylerule regel = new Stylerule(); // Nieuwe CSS-regel (selectors + body).
		push(regel); // Wordt de huidige container.
	}

	@Override
	public void exitRuleset(ICSSParser.RulesetContext ctx) {
		ASTNode regel = pop();	// Sluit de regel af.......
		hangAanOuder(regel);	// …EN TADA hang hem aan de ouder (stylesheet).
	}


	@Override
	public void enterIdSelector(ICSSParser.IdSelectorContext ctx) {
		String tekst = ctx.ID_IDENT().getText().substring(1); // strip '#'
		hangAanOuder(new IdSelector(tekst)); // Voeg aan huidige rule toe.
	}

	@Override
	public void enterClassSelector(ICSSParser.ClassSelectorContext ctx) {
		String tekst = ctx.CLASS_IDENT().getText().substring(1); // strip '.'
		hangAanOuder(new ClassSelector(tekst));
	}

	@Override
	public void enterTagSelector(ICSSParser.TagSelectorContext ctx) {
		String tekst = ctx.LOWER_IDENT().getText(); // gewone tagnaam zoals 'p' of 'div'
		hangAanOuder(new TagSelector(tekst));
	}

	@Override
	public void enterDeclaration(ICSSParser.DeclarationContext ctx) {
		Declaration declaratie = new Declaration(ctx.LOWER_IDENT().getText()); // property-naam
		push(declaratie); // Binnen deze declaratie komt eenn value expressie.
		startFrame(); // Frame starten zodat ik de value bij elkaar kan rapen.

	}

	@Override
	public void exitDeclaration(ICSSParser.DeclarationContext ctx) {
		int indexVoor = eindFrameIndex(); // Vanaf waar stonden de value-termen?
		java.util.List<Expression> waarden = pakOperandenSindsFrame(indexVoor);

		Declaration decl = (Declaration) top(); // Huidige declaratie
		if (!waarden.isEmpty()) {
			decl.addChild(waarden.get(0)); // De volledige value-expressie (zou al opgebouwd moeten zijn).
		} else {
			decl.addChild(new ScalarLiteral(0)); // zet 0 neer als er niks is.
		}
		ASTNode declaratie = pop();    // Klaar met deze declaratie, van de stack halen.
		hangAanOuder(declaratie); 	 // En ophangen aan de huidige ouder (stylerule).
	}

	@Override
	public void enterPrimaryExpr(ICSSParser.PrimaryExprContext ctx) {
		if (ctx.PIXELSIZE() != null) {
			int v = Integer.parseInt(ctx.PIXELSIZE().getText().replace("px", ""));
			exprPush(new PixelLiteral(v));
		} else if (ctx.PERCENTAGE() != null) {
			int v = Integer.parseInt(ctx.PERCENTAGE().getText().replace("%", ""));
			exprPush(new PercentageLiteral(v));
		} else if (ctx.SCALAR() != null) {
			exprPush(new ScalarLiteral(Integer.parseInt(ctx.SCALAR().getText())));
		} else if (ctx.COLOR() != null) {
			exprPush(new ColorLiteral(ctx.COLOR().getText()));
		} else if (ctx.TRUE() != null) {
			exprPush(new BoolLiteral(true));
		} else if (ctx.FALSE() != null) {
			exprPush(new BoolLiteral(false));
		} else if (ctx.CAPITAL_IDENT() != null) {
			exprPush(new VariableReference(ctx.CAPITAL_IDENT().getText()));
		}
	}

	// Vermenigvuldigen
	@Override
	public void enterMultiplicationExpr(ICSSParser.MultiplicationExprContext ctx) {
		startFrame(); // onthoud startpunt van de factoren (links dan rechts)
	}

	@Override
	public void exitMultiplicationExpr(ICSSParser.MultiplicationExprContext ctx) {
		int indexVoor = eindFrameIndex();
		java.util.List<Expression> factoren = pakOperandenSindsFrame(indexVoor);
		if (factoren.isEmpty()) return;
		// Linkse associativiteit: (((a*b)*c)*d) …
		Expression acc = factoren.get(0);
		for (int i = 1; i < factoren.size(); i++) {
			MultiplyOperation op = new MultiplyOperation();
			op.lhs = acc;
			op.rhs = factoren.get(i);
			acc = op;
		}
		exprPush(acc); // hele * ketting terug op de stack als 1 expressie
	}

	// dis voor optellen en aftrekken
	@Override
	public void enterAdditionExpr(ICSSParser.AdditionExprContext ctx) {
		startFrame(); // onthoud startpunt van de termen (links dan naar rechts)
	}

	@Override
	public void exitAdditionExpr(ICSSParser.AdditionExprContext ctx) {
		int indexVoor = eindFrameIndex();
		java.util.List<Expression> termen = pakOperandenSindsFrame(indexVoor);
		if (termen.isEmpty()) return;

		// operators in volgorde ( + of - ) uit de children halen
		java.util.List<String> operators = new java.util.ArrayList<>();
		for (int i = 0; i < ctx.getChildCount(); i++) {
			String t = ctx.getChild(i).getText();
			if ("+".equals(t) || "-".equals(t)) operators.add(t);
		}

		// Bouw links-associatief op: (((t0 op t1) op t2) op t3) …
		Expression acc = termen.get(0);
		for (int i = 0; i < operators.size(); i++) {
			Expression rhs = termen.get(i + 1);
			if ("+".equals(operators.get(i))) {
				AddOperation add = new AddOperation();
				add.lhs = acc;
				add.rhs = rhs;
				acc = add;
			} else {
				SubtractOperation sub = new SubtractOperation();
				sub.lhs = acc;
				sub.rhs = rhs;
				acc = sub;
			}
		}
		exprPush(acc); // complete optel/aftrek-expressie terug op de stack
	}

	// if clause (Dus TRUE of FALSE of var-ref)
	@Override
	public void enterBoolExpression(ICSSParser.BoolExpressionContext ctx) {
		// Maak de conditie-expressie uit de tekst
		String txt = ctx.getText();
		Expression cond;
		if ("TRUE".equals(txt)) {
			cond = new BoolLiteral(true);
		} else if ("FALSE".equals(txt)) {
			cond = new BoolLiteral(false);
		} else {
			cond = new VariableReference(txt);
		}
		exprPush(cond); // ook op de stack, voor consistentie
		ASTNode boven = top();
		if (boven instanceof IfClause) {
			((IfClause) boven).conditionalExpression = cond; // direct aan de if hangen
		}
	}

	// Variabele toekenning Var = Expr
	@Override
	public void enterVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
		VariableAssignment toekenning = new VariableAssignment(); // Nieuwe toekenning
		toekenning.name = new VariableReference(ctx.CAPITAL_IDENT().getText());
		push(toekenning);
		startFrame(); // waarde van de variabele
	}

	@Override
	public void exitVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
		int indexVoor = eindFrameIndex();
		java.util.List<Expression> waarden = pakOperandenSindsFrame(indexVoor);
		if (!waarden.isEmpty()) {
			((VariableAssignment) top()).addChild(waarden.get(0));
		}
		ASTNode klaar = pop();       // heel belangrijk: haal de assignment van de stack
		hangAanOuder(klaar);         // en hang ‘m aan de huidige ouder (Stylesheet)
	}

	// If-Clause begin en einde en optioneel Else
	@Override
	public void enterIfClause(ICSSParser.IfClauseContext ctx) {
		IfClause ifNode = new IfClause();
		// Conditie alvast proberen te vullen uit de parse tree (fallback naar TRUE).
		Expression cond;
		String txt = (ctx.boolExpression() != null) ? ctx.boolExpression().getText() : null;
		if ("TRUE".equals(txt)) {
			cond = new BoolLiteral(true);
		} else if ("FALSE".equals(txt)) {
			cond = new BoolLiteral(false);
		} else if (txt != null && !txt.isEmpty()) {
			cond = new VariableReference(txt);
		} else {
			cond = new BoolLiteral(true);
		}
		ifNode.conditionalExpression = cond;
		startFrame(); // mocht ik nog iets met de conditie willen tracken
		exprPush(cond); // consistent: ook op de expr-stack
		push(ifNode); // if-block is nu de actieve container

		elseActief = false;
		inIfVoorwaarde = false;
		ifCondBuffer.setLength(0);
	}

	@Override
	public void exitIfClause(ICSSParser.IfClauseContext ctx) {
		// Als we uit een else  komen, eerst die container poppen.
		if (elseActief) {
			try { pop(); } catch (Exception ignored) {}
			elseActief = false;
		}

		// Probeer een conditie uit het frame/buffer te halen als er nog niks was.
		int indexVoor = eindFrameIndex();
		java.util.List<Expression> conds = pakOperandenSindsFrame(indexVoor);
		if (conds.isEmpty()) {
			String raw = ifCondBuffer.toString().trim();
			if (!raw.isEmpty()) {
				if ("TRUE".equals(raw)) {
					conds.add(new BoolLiteral(true));
				} else if ("FALSE".equals(raw)) {
					conds.add(new BoolLiteral(false));
				} else {
					conds.add(new VariableReference(raw));
				}
			}
		}
		if (conds.isEmpty()) {
			conds.add(new BoolLiteral(true));
		}

		IfClause ifNodeObj = (IfClause) top();
		ifNodeObj.conditionalExpression = conds.get(0); // zet definitieve conditie

		ASTNode ifNode = pop(); // if klaar
		hangAanOuder(ifNode); // hang aan huidige ouder (ga gokken meestal Stylerule)
	}


	//Terminalen: ELSE en [ ... ] van de if
	@Override
	public void visitTerminal(org.antlr.v4.runtime.tree.TerminalNode node) {
		int t = node.getSymbol().getType();

		// Start van een else-blok, maak (indien nodig) elseClause aan en push ‘m.
		if (t == ICSSParser.ELSE && top() instanceof IfClause) {
			IfClause ifNode = (IfClause) top();
			if (ifNode.elseClause == null) {
				ifNode.elseClause = new ElseClause();
			}
			push(ifNode.elseClause);
			elseActief = true;
			return;
		}
		// Tekst van de if-voorwaarde bufferen tussen en (fallback-scenario’s).
		if (top() instanceof IfClause) {
			if (t == ICSSParser.BOX_BRACKET_OPEN) { inIfVoorwaarde = true; ifCondBuffer.setLength(0); return; }
			if (t == ICSSParser.BOX_BRACKET_CLOSE) { inIfVoorwaarde = false; return; }
			if (inIfVoorwaarde) {
				String txt = node.getText();
				if (txt != null && !txt.isEmpty()) ifCondBuffer.append(txt);
			}
		}
	}

	@Override
	public void enterBlok(ICSSParser.BlokContext ctx) {
	}

	@Override
	public void enterStatement(ICSSParser.StatementContext ctx) {
	}
}
