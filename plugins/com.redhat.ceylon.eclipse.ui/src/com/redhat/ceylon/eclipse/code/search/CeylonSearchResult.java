package com.redhat.ceylon.eclipse.code.search;

import static com.redhat.ceylon.eclipse.ui.CeylonResources.CEYLON_SEARCH_RESULTS;
import static org.eclipse.jdt.core.IJavaElement.CLASS_FILE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.IEditorMatchAdapter;
import org.eclipse.search.ui.text.IFileMatchAdapter;
import org.eclipse.search.ui.text.Match;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ide.FileStoreEditorInput;

import com.redhat.ceylon.eclipse.code.editor.SourceArchiveEditorInput;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;
import com.redhat.ceylon.eclipse.util.EditorUtil;

public class CeylonSearchResult extends AbstractTextSearchResult
        implements IEditorMatchAdapter, IFileMatchAdapter {
    
    private static final ImageDescriptor IMAGE = CeylonPlugin.getInstance()
            .getImageRegistry().getDescriptor(CEYLON_SEARCH_RESULTS);
    
    ISearchQuery query;
    
    CeylonSearchResult(ISearchQuery query) {
        this.query = query;
    }
    
    @Override
    public String getTooltip() {
        return getLabel();
    }

    @Override
    public ISearchQuery getQuery() {
        return query;
    }

    @Override
    public String getLabel() {
        return query.getLabel();
    }

    @Override
    public ImageDescriptor getImageDescriptor() {
        return IMAGE;
    }

    @Override
    public IFileMatchAdapter getFileMatchAdapter() {
        return this;
    }

    @Override
    public IEditorMatchAdapter getEditorMatchAdapter() {
        return this;
    }

    @Override
    public Match[] computeContainedMatches(AbstractTextSearchResult atsr,
            IFile file) {
        return getMatchesForFile(file);
    }

    public Match[] getMatchesForFile(IFile file) {
        List<Match> matches = new ArrayList<Match>();
        for (Object element: this.getElements()) {
            IFile elementFile = getFile(element);
            if (elementFile!=null && elementFile.equals(file)) {
                matches.addAll(Arrays.asList(getMatches(element)));
            }
        }
        return matches.toArray(new Match[matches.size()]);
    }
    
    public Match[] getMatchesForSourceArchive(SourceArchiveEditorInput input) {
        List<Match> matches = new ArrayList<Match>();
        String path = ((SourceArchiveEditorInput) input).getPath().toOSString();
        for (Object element: this.getElements()) {
            if (element instanceof CeylonElement) {
                String elementPath = ((CeylonElement) element).getVirtualFile().getPath();
                if (path.equals(elementPath)) {
                    matches.addAll(Arrays.asList(getMatches(element)));
                }
            }
        }
        return matches.toArray(new Match[matches.size()]);
    }
    
    private Match[] getMatchesForClassFile(IClassFile classFile) {
        List<Match> matches = new ArrayList<Match>();
        for (Object element: this.getElements()) {
            if (element instanceof IJavaElement) {
                IJavaElement elementClassFile = 
                        ((IJavaElement) element).getAncestor(CLASS_FILE);
                if (elementClassFile!=null && elementClassFile.equals(classFile)) {
                    matches.addAll(Arrays.asList(getMatches(element)));
                }
            }
        }
        return matches.toArray(new Match[matches.size()]);
    }
    
    public Match[] getMatchesForURI(URI uri) {
        List<Match> matches = new ArrayList<Match>();
        for (Object element: this.getElements()) {
            if (element instanceof CeylonElement) {
                String path = ((CeylonElement) element).getVirtualFile().getPath();
                if (uri.toString().endsWith(path)) {
                    matches.addAll(Arrays.asList(getMatches(element)));
                }
            }
            else if (element instanceof IJavaElement) {
                IResource resource = ((IJavaElement) element).getResource();
                if (resource!=null) {
                    String path = resource.getLocationURI().toString();
                    if (uri.toString().endsWith(path)) {
                        matches.addAll(Arrays.asList(getMatches(element)));
                    }
                }
            }
        }
        return matches.toArray(new Match[matches.size()]);
    }

    @Override
    public IFile getFile(Object element) {
        if (element instanceof IFile) {
            return (IFile) element;
        }
        else if (element instanceof CeylonElement) {
            return ((CeylonElement) element).getFile();
        }
        else if (element instanceof IJavaElement) {
            return (IFile) ((IJavaElement) element).getResource();
        }
        else { 
            return null;
        }
    }

    @Override
    public Match[] computeContainedMatches(AbstractTextSearchResult atsr,
            IEditorPart editor) {
        IEditorInput ei = editor.getEditorInput();
        if (ei instanceof SourceArchiveEditorInput) {
            return getMatchesForSourceArchive((SourceArchiveEditorInput) ei);
        }
        else if (ei instanceof IFileEditorInput) {
            return getMatchesForFile(EditorUtil.getFile(ei));
        }
        else if (ei instanceof FileStoreEditorInput) {
            return getMatchesForURI(((FileStoreEditorInput) ei).getURI());
        }
        else if (ei instanceof IClassFileEditorInput) {
            return getMatchesForClassFile(((IClassFileEditorInput) ei).getClassFile());
        }
        else {
            return new Match[0];
        }
    }

    @Override
    public boolean isShownInEditor(Match match, IEditorPart editor) {
        IEditorInput ei = editor.getEditorInput();
        Object element = match.getElement();
        if (ei instanceof IFileEditorInput) {
            IFile file = getFile(element);
            return file!=null && file.equals(EditorUtil.getFile(ei));
        }
        else if (ei instanceof FileStoreEditorInput) {
            String uri = ((FileStoreEditorInput) ei).getURI().toString();
            if (element instanceof CeylonElement) {
                String path = ((CeylonElement) element).getVirtualFile().getPath();
                return uri.endsWith(path);
            }
            else if (element instanceof IJavaElement) {
                String path = ((IJavaElement) element).getResource().getLocationURI().toString();
                return uri.endsWith(path);
            }
            else {
                return false;
            }
        }
        else if (ei instanceof IClassFileEditorInput) {
            if (element instanceof IJavaElement) {
                IClassFile classFile = ((IClassFileEditorInput) ei).getClassFile();
                return ((IJavaElement) element).getAncestor(IJavaElement.CLASS_FILE)==classFile;
            }
            else {
                return false;
            }
        }
        else {
            return false;
        }
    }
    
}