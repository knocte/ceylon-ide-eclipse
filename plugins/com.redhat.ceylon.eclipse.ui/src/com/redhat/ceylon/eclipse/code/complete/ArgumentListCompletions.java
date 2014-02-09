package com.redhat.ceylon.eclipse.code.complete;

import static com.redhat.ceylon.eclipse.code.complete.InvocationCompletionProposal.addInvocationProposals;

import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.DeclarationWithProximity;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;

public class ArgumentListCompletions {
	
	public static void addArgumentListProposal(final int offset,
			final CeylonParseController cpc, final Node node,
			final Scope scope, final IDocument document,
			final List<ICompletionProposal> result) {
		final Integer startIndex2 = node.getStartIndex();
		final Integer stopIndex2 = node.getStopIndex();
		final String typeArgText;
		try {
			typeArgText = document.get(startIndex2, stopIndex2-startIndex2+1);
		} 
		catch (BadLocationException e) {
			e.printStackTrace();
			return;
		}
		new Visitor() {
			@Override
			public void visit(Tree.StaticMemberOrTypeExpression that) {
				Integer startIndex = that.getTypeArguments().getStartIndex();
				if (startIndex!=null && startIndex2!=null &&
					startIndex.intValue()==startIndex2.intValue()) {
					ProducedReference pr = that.getTarget();
					Declaration d = that.getDeclaration();
					if (d instanceof Functional && pr!=null) {
						try {
							String pref = document.get(that.getStartIndex(), 
									that.getStopIndex()-that.getStartIndex()+1);
				        	addInvocationProposals(offset, pref, cpc, result, 
				        			new DeclarationWithProximity(d, 0), pr, 
				        			scope, null, typeArgText);
						} 
						catch (BadLocationException e) {
							e.printStackTrace();
						}
					}
				}
				super.visit(that);
			}
			public void visit(Tree.SimpleType that) {
				Integer startIndex = that.getTypeArgumentList().getStartIndex();
				if (startIndex!=null && startIndex2!=null &&
					startIndex.intValue()==startIndex2.intValue()) {
					Declaration d = that.getDeclarationModel();
					if (d instanceof Functional) {
						try {
							String pref = document.get(that.getStartIndex(), 
									that.getStopIndex()-that.getStartIndex()+1);
				        	addInvocationProposals(offset, pref, cpc, result, 
				        			new DeclarationWithProximity(d, 0), 
				        			    that.getTypeModel(), scope, 
				        			    null, typeArgText);
						}
						catch (BadLocationException e) {
							e.printStackTrace();
						}
					}
				}
				super.visit(that);
			}
		}.visit(cpc.getRootNode());
	}

}
