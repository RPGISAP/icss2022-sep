package nl.han.ica.icss.parser;

import nl.han.ica.datastructures.HANStack;
import nl.han.ica.datastructures.IHANStack;
import nl.han.ica.icss.ast.AST;
import nl.han.ica.icss.ast.ASTNode;
import nl.han.ica.icss.ast.Expression;

public class ASTListener extends ICSSBaseListener {

	private final AST ast;
	private final IHANStack<ASTNode> containerStack;
	private final HANStack<Expression> expressieStack = new HANStack<>();
	private final HANStack<Integer> frameStack = new HANStack<>();
	private int expressieStackGrootte = 0;
	// else-detectie tijdens if
	private boolean elseActief = false;

	public ASTListener() {
		this.ast = new AST();
		this.containerStack = new HANStack<>();
	}

	public AST getAST() {
		return ast;
	}

	private void push(ASTNode node) {
		containerStack.push(node);
	}
	private ASTNode pop() { return containerStack.pop(); }
	private ASTNode top() { return containerStack.peek(); }
	private void hangAanOuder(ASTNode kind) {
		// Bovenste element is ouder; hang kind eraan
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
		push(ast.root); // ast.root is een Stylesheet
	}
	@Override
	public void exitStylesheet(ICSSParser.StylesheetContext ctx) {
		pop(); // klaar
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
		if (!waarden.isEmpty()) {
			((nl.han.ica.icss.ast.Declaration) top()).addChild(waarden.get(0));
		}
		ASTNode declaratie = pop();
		hangAanOuder(declaratie);
	}

	@Override
	public void enterPrimaryExpr(ICSSParser.PrimaryExprContext ctx) {
		if (ctx.PIXELSIZE()!=null) {
			int v = Integer.parseInt(ctx.PIXELSIZE().getText().replace("px",""));
			exprPush(new nl.han.ica.icss.ast.literals.PixelLiteral(v));
		} else if (ctx.PERCENTAGE()!=null) {
			int v = Integer.parseInt(ctx.PERCENTAGE().getText().replace("%",""));
			exprPush(new nl.han.ica.icss.ast.literals.PercentageLiteral(v));
		} else if (ctx.SCALAR()!=null) {
			exprPush(new nl.han.ica.icss.ast.literals.ScalarLiteral(Integer.parseInt(ctx.SCALAR().getText())));
		} else if (ctx.COLOR()!=null) {
			exprPush(new nl.han.ica.icss.ast.literals.ColorLiteral(ctx.COLOR().getText()));
		} else if (ctx.TRUE()!=null) {
			exprPush(new nl.han.ica.icss.ast.literals.BoolLiteral(true));
		} else if (ctx.FALSE()!=null) {
			exprPush(new nl.han.ica.icss.ast.literals.BoolLiteral(false));
		} else if (ctx.CAPITAL_IDENT()!=null) {
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
				add.lhs = acc; add.rhs = rhs; acc = add;
			} else {
				nl.han.ica.icss.ast.operations.SubtractOperation sub = new nl.han.ica.icss.ast.operations.SubtractOperation();
				sub.lhs = acc; sub.rhs = rhs; acc = sub;
			}
		}
		exprPush(acc);
	}

	@Override
	public void enterBoolExpression(ICSSParser.BoolExpressionContext ctx) {
		if (ctx.TRUE() != null) {
			exprPush(new nl.han.ica.icss.ast.literals.BoolLiteral(true));
		} else if (ctx.FALSE() != null) {
			exprPush(new nl.han.ica.icss.ast.literals.BoolLiteral(false));
		} else if (ctx.CAPITAL_IDENT() != null) {
			exprPush(new nl.han.ica.icss.ast.VariableReference(ctx.CAPITAL_IDENT().getText()));
		}
	}

	@Override
	public void enterVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
		nl.han.ica.icss.ast.VariableAssignment toekenning = new nl.han.ica.icss.ast.VariableAssignment(); // no-args ctor
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
		ASTNode klaar = pop();
		hangAanOuder(klaar);
	}

	@Override
	public void enterIfClause(ICSSParser.IfClauseContext ctx) {
		nl.han.ica.icss.ast.IfClause ifNode = new nl.han.ica.icss.ast.IfClause();
		push(ifNode);
		elseActief = false;
		startFrame(); // condition-expressie (tussen de [ ])
	}

	@Override
	public void exitIfClause(ICSSParser.IfClauseContext ctx) {
		// als er een ElseClause open staat, eerst poppen
		if (elseActief) {
			try { pop(); } catch (Exception ignored) {}
			elseActief = false;
		}
		int indexVoor = eindFrameIndex();
		java.util.List<Expression> conds = pakOperandenSindsFrame(indexVoor);
		if (!conds.isEmpty()) {
			((nl.han.ica.icss.ast.IfClause) top()).addChild(conds.get(0)); // wordt conditionalExpression
		}
		ASTNode ifNode = pop();
		hangAanOuder(ifNode);
	}

	@Override
	public void visitTerminal(org.antlr.v4.runtime.tree.TerminalNode node) {
		if (node.getSymbol().getType() == ICSSParser.ELSE && top() instanceof nl.han.ica.icss.ast.IfClause) {
			nl.han.ica.icss.ast.ElseClause elseClause = new nl.han.ica.icss.ast.ElseClause();
			top().addChild(elseClause);
			push(elseClause);      // alles wat hierna in het blok komt, valt in else.body
			elseActief = true;
		}
	}

	@Override
	public void enterBlok(ICSSParser.BlokContext ctx) {
	}

	@Override
	public void enterStatement(ICSSParser.StatementContext ctx) {
	}


}
