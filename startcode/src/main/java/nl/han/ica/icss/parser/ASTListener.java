package nl.han.ica.icss.parser;

import nl.han.ica.datastructures.HANStack;
import nl.han.ica.datastructures.IHANStack;
import nl.han.ica.icss.ast.AST;
import nl.han.ica.icss.ast.ASTNode;

public class ASTListener extends ICSSBaseListener {

	private final AST ast;
	private final IHANStack<ASTNode> containerStack;

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
		// Maak een RuleSet of Stylerule node, afhankelijk van jouw AST (meestal Stylerule)
		nl.han.ica.icss.ast.Stylerule regel = new nl.han.ica.icss.ast.Stylerule();
		// Hang pas aan ouder bij exit (als body ook gevuld is), dus push nu
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
	}

	@Override
	public void exitDeclaration(ICSSParser.DeclarationContext ctx) {
		ASTNode declaratie = pop();
		hangAanOuder(declaratie);
	}

	@Override
	public void enterPrimaryExpr(ICSSParser.PrimaryExprContext ctx) {
		if (ctx.PIXELSIZE()!=null) {
			String v = ctx.PIXELSIZE().getText().replace("px","");
			hangAanOuder(new nl.han.ica.icss.ast.literals.PixelLiteral(Integer.parseInt(v)));
		} else if (ctx.PERCENTAGE()!=null) {
			String v = ctx.PERCENTAGE().getText().replace("%","");
			hangAanOuder(new nl.han.ica.icss.ast.literals.PercentageLiteral(Integer.parseInt(v)));
		} else if (ctx.SCALAR()!=null) {
			hangAanOuder(new nl.han.ica.icss.ast.literals.ScalarLiteral(Integer.parseInt(ctx.SCALAR().getText())));
		} else if (ctx.COLOR()!=null) {
			hangAanOuder(new nl.han.ica.icss.ast.literals.ColorLiteral(ctx.COLOR().getText()));
		} else if (ctx.TRUE()!=null) {
			hangAanOuder(new nl.han.ica.icss.ast.literals.BoolLiteral(true));
		} else if (ctx.FALSE()!=null) {
			hangAanOuder(new nl.han.ica.icss.ast.literals.BoolLiteral(false));
		} else if (ctx.CAPITAL_IDENT()!=null) {
			nl.han.ica.icss.ast.VariableReference ref = new nl.han.ica.icss.ast.VariableReference(ctx.CAPITAL_IDENT().getText());
			hangAanOuder(ref);
		}
	}

	@Override
	public void exitAdditionExpr(ICSSParser.AdditionExprContext ctx) {
		if (ctx.multiplicationExpr().size() == 1) return; // enkel element
	}

	@Override
	public void enterVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
		nl.han.ica.icss.ast.VariableAssignment toekenning = new nl.han.ica.icss.ast.VariableAssignment(); // no-args ctor
		toekenning.name = new nl.han.ica.icss.ast.VariableReference(ctx.CAPITAL_IDENT().getText());
		push(toekenning);
	}
	@Override
	public void exitVariableAssignment(ICSSParser.VariableAssignmentContext ctx) {
		ASTNode klaar = pop();
		hangAanOuder(klaar);
	}

	@Override
	public void enterIfClause(ICSSParser.IfClauseContext ctx) {
		nl.han.ica.icss.ast.IfClause ifNode = new nl.han.ica.icss.ast.IfClause();
		push(ifNode);
	}
	@Override
	public void exitIfClause(ICSSParser.IfClauseContext ctx) {
		ASTNode ifNode = pop();
		hangAanOuder(ifNode);
	}

	@Override
	public void enterBlok(ICSSParser.BlokContext ctx) {
	}

	@Override
	public void enterStatement(ICSSParser.StatementContext ctx) {
	}


}
