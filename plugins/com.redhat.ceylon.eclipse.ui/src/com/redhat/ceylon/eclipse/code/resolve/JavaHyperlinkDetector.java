package com.redhat.ceylon.eclipse.code.resolve;

import static com.redhat.ceylon.eclipse.util.Nodes.findNode;
import static com.redhat.ceylon.eclipse.util.Nodes.getIdentifyingNode;
import static com.redhat.ceylon.eclipse.util.Nodes.getReferencedDeclaration;
import static org.eclipse.jdt.internal.ui.javaeditor.EditorUtility.openInEditor;
import static org.eclipse.jdt.internal.ui.javaeditor.EditorUtility.revealInEditor;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Referenceable;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.core.model.CeylonBinaryUnit;
import com.redhat.ceylon.eclipse.core.model.ExternalSourceFile;
import com.redhat.ceylon.eclipse.core.model.IJavaModelAware;

public class JavaHyperlinkDetector implements IHyperlinkDetector {

    private CeylonParseController pc;
    
    public JavaHyperlinkDetector(CeylonParseController pc) {
        this.pc = pc;
    }
    
    private final class JavaElementLink implements IHyperlink {
        private final IJavaElement elem;
        private final Node id;

        private JavaElementLink(IJavaElement elem, Node id) {
            this.elem = elem;
            this.id = id;
        }

        @Override
        public void open() {
            try {
                IEditorPart part = openInEditor(elem, true);
                if (part!=null) {
                    revealInEditor(part, elem);
                }
            } 
            catch (PartInitException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String getTypeLabel() {
            return null;
        }

        @Override
        public String getHyperlinkText() {
            return "Java Declaration";
        }

        @Override
        public IRegion getHyperlinkRegion() {
            return new Region(id.getStartIndex(), 
                    id.getStopIndex()-id.getStartIndex()+1);
        }
    }

    @Override
    public IHyperlink[] detectHyperlinks(ITextViewer tv, IRegion region,
            boolean csmh) {
        if (pc==null||pc.getRootNode()==null) {
            return null;
        }
        else {
            Node node = findNode(pc.getRootNode(), region.getOffset(), 
                    region.getOffset()+region.getLength());
            if (node==null) {
                return null;
            }
            else {
                Node id = getIdentifyingNode(node);
                Referenceable ref = getReferencedDeclaration(node);
                if (ref instanceof Declaration) {
                    Declaration dec = (Declaration) ref;
                    Unit declarationUnit = dec.getUnit();
                    IJavaProject jp = JavaCore.create(pc.getProject());
                    
                    if (!(declarationUnit instanceof IJavaModelAware)) {
                        if (declarationUnit instanceof ExternalSourceFile) {
                            Declaration binaryDeclaration = ((ExternalSourceFile)declarationUnit).retrieveBinaryDeclaration(dec);
                            if (binaryDeclaration != null) {
                                dec = binaryDeclaration;
                                declarationUnit = binaryDeclaration.getUnit();
                            } else {
                                return null;
                            }
                        } else {
                            return null;
                        }
                    } else {
                        jp = ((IJavaModelAware)declarationUnit).getTypeRoot().getJavaProject();
                    }
                    if (declarationUnit instanceof CeylonBinaryUnit) {
                        CeylonBinaryUnit ceylonBinaryUnit = (CeylonBinaryUnit) declarationUnit;
                        if (! JavaCore.isJavaLikeFileName(ceylonBinaryUnit.getSourceRelativePath())) {
                            return null; 
                        }
                        jp = ceylonBinaryUnit.getTypeRoot().getJavaProject();
                    }
                    if (jp==null) {
                        return null;
                    }
                    else {
                        try {
                            IJavaElement element = getJavaElement(dec);
                            if (element==null) {
                                return null;
                            }
                            else {
                                return new IHyperlink[] { new JavaElementLink(element, id) };
                            }
                        }
                        catch (JavaModelException jme) {
                            jme.printStackTrace();
                            return null;
                        }
                    }
                }
                else {
                    return null;
                }
            }
        }
    }
    
    public static void gotoJavaNode(Declaration dec) {
        try {
            IJavaElement element = getJavaElement(dec);
            if (element!=null) {
                IEditorPart part = openInEditor(element, true);
                if (part!=null) {
                    revealInEditor(part, element);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static IJavaElement getJavaElement(Declaration dec)
            throws JavaModelException {
        if (dec.getUnit() instanceof IJavaModelAware) {
            return ((IJavaModelAware) dec.getUnit()).toJavaElement(dec);
        }
        return null;
    }
}
