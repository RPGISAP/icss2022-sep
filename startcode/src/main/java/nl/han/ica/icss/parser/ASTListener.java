package nl.han.ica.icss.parser;

import nl.han.ica.datastructures.HANStack;
import nl.han.ica.datastructures.IHANStack;
import nl.han.ica.icss.ast.AST;
import nl.han.ica.icss.ast.ASTNode;
import nl.han.ica.icss.ast.Expression;
import nl.han.ica.icss.ast.VariableReference;

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
		this.ast = new AST();
		this.containerStack = new HANStack<>();
		System.out.println("ASTListener geladen van: " +
				ASTListener.class.getProtectionDomain().getCodeSource().getLocation());
	}


	public AST getAST() {
		return ast;
	}

	private void push(ASTNode node) {
		containerStack.push(node);
	}

	private ASTNode pop() {
		return containerStack.pop();
	}

	private ASTNode top() {
		return containerStack.peek();
	}

	private void hangAanOuder(ASTNode kind) {
		if (kind == null) return;   // << voorkom null-kind in AST
		top().addChild(kind);
	}

	private void startFrame() {
		frameStack.push(expressieStackGrootte);
	}

	private int eindFrameIndex() {
		return frameStack.pop();
	}

	private void exprPush(Expression e) {
		expressieStack.push(e);
		expressieStackGrootte++;
	}

	private Expression exprPop() {
		Expression e = expressieStack.pop();
		expressieStackGrootte--;
		return e;
	}

	private java.util.List<Expression> pakOperandenSindsFrame(int indexVoor) {
		int aantal = expressieStackGrootte - indexVoor;
		java.util.List<Expression> exprs = new java.util.ArrayList<>(aantal);
		for (int i = 0; i < aantal; i++) {
			// pop in LIFO; vooraan toevoegen om volgorde links->rechts te behouden
			exprs.add(0, exprPop());
		}
		return exprs;
	}


	@Override
	public void enterStylesheet(ICSSParser.StylesheetContext ctx) {
		nl.han.ica.icss.ast.Stylesheet sheet = new nl.han.ica.icss.ast.Stylesheet();
		ast.setRoot(sheet);
		push(sheet);
	}

	@Override
	public void exitStylesheet(ICSSParser.StylesheetContext ctx) {
		pop(); // done
	}

	@Override
	public void enterRuleset(ICSSParser.RulesetContext ctx) {
		nl.han.ica.icss.ast.Stylerule regel = new nl.han.ica.icss.ast.Stylerule();
		push(regel);
	}

	@Override
	public void exitRuleset(ICSSParser.RulesetContext ctx) {
		ASTNode regel = pop();
		hangAanOuder(regel);
	}

	@Override
	public void enterIdSelector(ICSSParser.IdSelectorContext ctx) {
		String tekst = ctx.ID_IDENT().getText().substring(1); // strip '#'
		hangAanOuder(new nl.han.ica.icss.ast.selectors.IdSelector(tekst));
	}

	@Override
	public void enterClassSelector(ICSSParser.ClassSelectorContext ctx) {
		String tekst = ctx.CLASS_IDENT().getText().substring(1); // strip '.'
		hangAanOuder(new nl.han.ica.icss.ast.selectors.ClassSelector(tekst));
	}

	@Override
	public void enterTagSelector(ICSSParser.TagSelectorContext ctx) {
		String tekst = ctx.LOWER_IDENT().getText();
		hangAanOuder(new nl.han.ica.icss.ast.selectors.TagSelector(tekst));
	}

	@Override
	public void enterDeclaration(ICSSParser.DeclarationContext ctx) {
		nl.han.ica.icss.ast.Declaration declaratie = new nl.han.ica.icss.ast.Declaration(ctx.LOWER_IDENT().getText());
		push(declaratie);
		startFrame();

	}

	@Override
	public void exitDeclaration(ICSSParser.DeclarationContext ctx) {
		int indexVoor = eindFrameIndex();
		java.util.List<Expression> waarden = pakOperandenSindsFrame(indexVoor);

		nl.han.ica.icss.ast.Declaration decl = (nl.han.ica.icss.ast.Declaration) top();
		if (!waarden.isEmpty()) {
			decl.addChild(waarden.get(0));
		} else {
			decl.addChild(new nl.han.ica.icss.ast.literals.ScalarLiteral(0));
		}
		ASTNode declaratie = pop();
		hangAanOuder(declaratie);
	}

	@Override
	public void enterPrimaryExpr(ICSSParser.PrimaryExprContext ctx) {
		if (ctx.PIXELSIZE() != null) {
			int v = Integer.parseInt(ctx.PIXELSIZE().getText().replace("px", ""));
			exprPush(new nl.han.ica.icss.ast.literals.PixelLiteral(v));
		} else if (ctx.PERCENTAGE() != null) {
			int v = Integer.parseInt(ctx.PERCENTAGE().getText().replace("%", ""));
			exprPush(new nl.han.ica.icss.ast.literals.PercentageLiteral(v));
		} else if (ctx.SCALAR() != null) {
			exprPush(new nl.han.ica.icss.ast.literals.ScalarLiteral(Integer.parseInt(ctx.SCALAR().getText())));
		} else if (ctx.COLOR() != null) {
			exprPush(new nl.han.ica.icss.ast.literals.ColorLiteral(ctx.COLOR().getText()));
		} else if (ctx.TRUE() != null) {
			exprPush(new nl.han.ica.icss.ast.literals.BoolLiteral(true));
		} else if (ctx.FALSE() != null) {
			exprPush(new nl.han.ica.icss.ast.literals.BoolLiteral(false));
		} else if (ctx.CAPITAL_IDENT() != null) {
			exprPush(new nl.han.ica.icss.ast.VariableReference(ctx.CAPITAL_IDENT().getText()));
		}
	}

	@Override
	public void enterMultiplicationExpr(ICSSParser.MultiplicationExprContext ctx) {
		startFrame();
	}

	@Override
	public void exitMultiplicationExpr(ICSSParser.MultiplicationExprContext ctx) {
		int indexVoor = eindFrameIndex();
		java.util.List<Expression> factoren = pakOperandenSindsFrame(indexVoor);
		if (factoren.isEmpty()) return;
		Expression acc = factoren.get(0);
		for (int i = 1; i < factoren.size(); i++) {
			nl.han.ica.icss.ast.operations.MultiplyOperation op = new nl.han.ica.icss.ast.operations.MultiplyOperation();
			op.lhs = acc;
			op.rhs = factoren.get(i);
			acc = op;
		}
		exprPush(acc);
	}

	@Override
	public void enterAdditionExpr(ICSSParser.AdditionExprContext ctx) {
		startFrame();
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

		Expression acc = termen.get(0);
		for (int i = 0; i < operators.size(); i++) {
			Expression rhs = termen.get(i + 1);
			if ("+".equals(operators.get(i))) {
				nl.han.ica.icss.ast.operations.AddOperation add = new nl.han.ica.icss.ast.operations.AddOperation();
				add.lhs = acc;
				add.rhs = rhs;
				acc = add;
			} else {
				nl.han.ica.icss.ast.operations.SubtractOperation sub = new nl.han.ica.icss.ast.operations.SubtractOperation();
				sub.lhs = acc;
				sub.rhs = rhs;
				acc = sub;
			}
		}
		exprPush(acc);
	}

	@Override
	public void enterBoolExpression(ICSSParser.BoolExpressionContext ctx) {
		// Maak de conditie-expressie uit de tekst
		String txt = ctx.getText();
		nl.han.ica.icss.ast.Expression cond;
		if ("TRUE".equals(txt)) {
			cond = new nl.han.ica.icss.ast.literals.BoolLiteral(true);
		} else if ("FALSE".equals(txt)) {
			cond = new nl.han.ica.icss.ast.literals.BoolLiteral(false);
		} else {
			cond = new nl.han.ica.icss.ast.VariableReference(txt);
		}
		exprPush(cond);
		ASTNode boven = top();
		if (boven instanceof nl.han.ica.icss.ast.IfClause) {
			((nl.han.ica.icss.ast.IfClause) boven).conditionalExpression = cond;
		}
	}



	@Override
	public void enterVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
		nl.han.ica.icss.ast.VariableAssignment toekenning = new nl.han.ica.icss.ast.VariableAssignment();
		toekenning.name = new nl.han.ica.icss.ast.VariableReference(ctx.CAPITAL_IDENT().getText());
		push(toekenning);
		startFrame(); // waarde van de variabele
	}

	@Override
	public void exitVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
		int indexVoor = eindFrameIndex();
		java.util.List<Expression> waarden = pakOperandenSindsFrame(indexVoor);
		if (!waarden.isEmpty()) {
			((nl.han.ica.icss.ast.VariableAssignment) top()).addChild(waarden.get(0));
		}
		ASTNode klaar = pop();       // heel belangrijk: haal de assignment van de stack
		hangAanOuder(klaar);         // en hang ‘m aan de huidige ouder (Stylesheet)
	}

	@Override
	public void enterIfClause(ICSSParser.IfClauseContext ctx) {
		nl.han.ica.icss.ast.IfClause ifNode = new nl.han.ica.icss.ast.IfClause();

		nl.han.ica.icss.ast.Expression cond;
		String txt = (ctx.boolExpression() != null) ? ctx.boolExpression().getText() : null;
		if ("TRUE".equals(txt)) {
			cond = new nl.han.ica.icss.ast.literals.BoolLiteral(true);
		} else if ("FALSE".equals(txt)) {
			cond = new nl.han.ica.icss.ast.literals.BoolLiteral(false);
		} else if (txt != null && !txt.isEmpty()) {
			cond = new nl.han.ica.icss.ast.VariableReference(txt);
		} else {
			cond = new nl.han.ica.icss.ast.literals.BoolLiteral(true);
		}
		ifNode.conditionalExpression = cond;
		startFrame();
		exprPush(cond);
		push(ifNode);

		elseActief = false;
		inIfVoorwaarde = false;
		ifCondBuffer.setLength(0);
	}

	@Override
	public void exitIfClause(ICSSParser.IfClauseContext ctx) {
		if (elseActief) {
			try { pop(); } catch (Exception ignored) {}
			elseActief = false;
		}
		int indexVoor = eindFrameIndex();
		java.util.List<Expression> conds = pakOperandenSindsFrame(indexVoor);
		if (conds.isEmpty()) {
			String raw = ifCondBuffer.toString().trim();
			if (!raw.isEmpty()) {
				if ("TRUE".equals(raw)) {
					conds.add(new nl.han.ica.icss.ast.literals.BoolLiteral(true));
				} else if ("FALSE".equals(raw)) {
					conds.add(new nl.han.ica.icss.ast.literals.BoolLiteral(false));
				} else {
					conds.add(new nl.han.ica.icss.ast.VariableReference(raw));
				}
			}
		}
		if (conds.isEmpty()) {
			conds.add(new nl.han.ica.icss.ast.literals.BoolLiteral(true));
		}

		nl.han.ica.icss.ast.IfClause ifNodeObj = (nl.han.ica.icss.ast.IfClause) top();
		ifNodeObj.conditionalExpression = conds.get(0);

		ASTNode ifNode = pop();
		hangAanOuder(ifNode);
	}


	@Override
	public void visitTerminal(org.antlr.v4.runtime.tree.TerminalNode node) {
		int t = node.getSymbol().getType();
		if (t == ICSSParser.ELSE && top() instanceof nl.han.ica.icss.ast.IfClause) {
			nl.han.ica.icss.ast.IfClause ifNode = (nl.han.ica.icss.ast.IfClause) top();
			if (ifNode.elseClause == null) {
				ifNode.elseClause = new nl.han.ica.icss.ast.ElseClause();
			}
			push(ifNode.elseClause);
			elseActief = true;
			return;
		}
		if (top() instanceof nl.han.ica.icss.ast.IfClause) {
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
